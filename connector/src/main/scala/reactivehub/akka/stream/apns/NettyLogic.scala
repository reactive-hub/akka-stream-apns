package reactivehub.akka.stream.apns

import akka.stream.FlowShape
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE
import io.netty.channel._
import io.netty.channel.socket.{ChannelInputShutdownEvent, SocketChannel}
import io.netty.util.concurrent.PromiseCombiner
import java.nio.channels.ClosedChannelException
import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

/**
  * A flow-shaped GraphStageLogic which writes and reads from a Netty channel.
  */
private[apns] abstract class NettyLogic[I, O: ClassTag](
  shape: FlowShape[I, O],
  inputBufferMaxSize: Int = 16,
  outputBufferSoftLimit: Int = 16,
  outputBufferMaxSize: Int = Integer.MAX_VALUE)
    extends GraphStageLogic(shape) {

  require(inputBufferMaxSize >= 0)
  require(outputBufferSoftLimit >= 0)
  require(outputBufferMaxSize >= 0)
  require(outputBufferSoftLimit <= outputBufferMaxSize)

  /**
    * Underlying Netty connection. Accessed from the stage thread.
    */
  private var channel: Channel = _

  override def preStart(): Unit = {
    setKeepGoing(true)
    val f = createChannel(new Bridge)
    channel = f.channel()

    // If the channel is successfully connected to the remote peer, a new close
    // listener is installed to the channel; otherwise the stage is completed
    // with the same error as caused the failure. The close listeners handles
    // situations when the channel is closed before the bridge is ready.
    f.addListener(new ChannelFutureListener {
      override def operationComplete(f: ChannelFuture): Unit =
        if (f.isSuccess)
          f.channel().closeFuture().addListener(new ChannelFutureListener {
            override def operationComplete(f: ChannelFuture): Unit =
              channelClosed(())
          })
        else connectFailed(f.cause())
    })
  }

  /**
    * Builds a new channel. When the channel is initialized, the bridge should
    * be added to the end of the channel pipeline so that the flow communicates
    * with the channel.
    */
  protected def createChannel(bridge: ChannelHandler): ChannelFuture

  /**
    * Indicates that either the bridge has not yet been added to the channel
    * pipeline or the channel is not active. Accessed from the stage thread.
    */
  private var connecting = true

  /**
    * Completes the stage when the channel closes before the bridge is ready.
    */
  private val channelClosed = getAsyncCallback[Unit] { _ ⇒
    if (connecting) completeStage()
  } invoke _

  /**
    * Completes the stage when the channel cannot connect to the remote peer.
    */
  private val connectFailed = getAsyncCallback[Throwable](failStage) invoke _

  /**
    * Buffer of pre-fetched input elements. Maximum size of the buffer is
    * inputBufferMaxSize. The bridge allows that the whole buffer is written
    * to the channel and all elements are properly transferred one by one.
    * Accessed from the stage thread.
    */
  private var inputBuffer = mutable.Queue.empty[I]

  /**
    * Indicates that the channel signaled it was ready to handle more data but
    * the input buffer was empty at that time. When an element is grabbed from
    * the input port next time it will be immediately written to the channel.
    * Accessed from the stage thread.
    */
  private var writeOnPush = false

  // Set a handler for the input port. It is safe to use the same handler
  // during the whole lifecycle of the stage because no element is pulled
  // before the bridge is ready.
  setHandler(shape.in, new InHandler {
    override def onPush(): Unit = {
      if (writeOnPush) {
        writeAndFlush(channel, grab(shape.in))
        writeOnPush = false
      } else inputBuffer += grab(shape.in)
      if (inputBuffer.size < inputBufferMaxSize) pull(shape.in)
    }

    override def onUpstreamFinish(): Unit =
      if (inputBuffer.isEmpty) closeWrite()

    override def onUpstreamFailure(cause: Throwable): Unit = fail(cause)
  })

  /**
    * Writes one or more elements to the channel and flushes it. When the data
    * is successfully written and the channel is still writable, more data is
    * requested. Otherwise if the write operation has failed the stage is
    * completed with the same error as caused the failure. Invoked from the
    * stage thread.
    */
  private def writeAndFlush(channel: Channel, msg: Any): Unit = {
    channel.writeAndFlush(msg).addListener(new ChannelFutureListener {
      override def operationComplete(f: ChannelFuture): Unit =
        if (f.isSuccess) {
          if (channel.isWritable) pullInput(())
        } else writeFailed(f.cause())
    })
  }

  /**
    * Tries to close the write side of the channel. If this is not possible
    * (either because the channel is not a SocketChannel or it is not active
    * or it does not allow half-closure or the output has already been closed),
    * the whole channel is closed. Invoked from the stage thread.
    */
  private def closeWrite(): Unit = channel match {
    case c: SocketChannel if canHalfClose(c) ⇒ c.shutdownOutput()
    case c                                   ⇒ c.close()
  }

  /**
    * Checks if the channel supports half closing.
    */
  private def canHalfClose(channel: Channel): Boolean =
    !isClosed(shape.out) && channel.isActive && channel.config()
      .getOption(ALLOW_HALF_CLOSURE).booleanValue

  /**
    * Closes the output port with the error and closes the channel. Invoked
    * from the stage thread.
    */
  private def fail(cause: Throwable): Unit = {
    fail(shape.out, cause)
    if (!isClosed(shape.in)) cancel(shape.in)
    channel.close()
  }

  /**
    * Requests more data from the input.
    */
  private val pullInput = getAsyncCallback[Unit](_ ⇒ writeOrPull()) invoke _

  /**
    * Either writes the input buffer to the channel if non-empty or pulls
    * an element from the input. If the input has already been closed, it
    * closes the write side of the channel or the whole channel. Invoked
    * from the stage thread.
    */
  private def writeOrPull(): Unit = {
    if (inputBuffer.nonEmpty) {
      val tmp = inputBuffer
      inputBuffer = mutable.Queue.empty[I]
      writeAndFlush(channel, tmp)
      if (!hasBeenPulled(shape.in)) tryPull(shape.in)
    } else if (!isClosed(shape.in)) {
      writeOnPush = true
      if (!hasBeenPulled(shape.in)) pull(shape.in)
    } else closeWrite()
  }

  /**
    * Completes the stage when write fails.
    */
  private val writeFailed = getAsyncCallback[Throwable](fail) invoke _

  private class Bridge extends ChannelDuplexHandler {
    /**
      * ChannelHandlerContext associated with this handler. Accessed from
      * the I/O thread.
      */
    private var ctx: ChannelHandlerContext = _

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
      this.ctx = ctx
      if (ctx.channel().isActive) bridgeReady(this)
    }

    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      bridgeReady(this)
      ctx.fireChannelActive()
    }

    /**
      * The last unprocessed queue written to the channel. Null if there
      * is no queue pending. Accessed from the I/O thread.
      */
    private var writeBuffer: mutable.Queue[_] = _

    /**
      * The promise of the last queue written. Accessed from the I/O thread.
      */
    private var writePromise: ChannelPromise = _

    private def writePending: Boolean = writeBuffer != null

    private def resetWriteBuffer(): Unit = {
      writeBuffer = null
      writePromise = null
    }

    /**
      * Handles channel writes. If a queue of elements is written to
      * the channel, it transfers individual elements of the queue one by one.
      */
    override def write(ctx: ChannelHandlerContext, msg: Any,
      promise: ChannelPromise): Unit =
      msg match {
        case buffer: mutable.Queue[_] ⇒
          if (writePending) throw new IllegalStateException(
            "Cannot write new messages while the previous are not flushed")

          writeBuffer = buffer
          writePromise = promise
          flushWriteBuffer(ctx)

        case _ ⇒ ctx.write(msg, promise)
      }

    /**
      * Writes as many write buffer elements as possible. If not all elements
      * are written, the transfer is resumed after the channel becomes
      * writable again. Original write promise is completed successfully if
      * and only if all elements succeed.
      */
    private def flushWriteBuffer(ctx: ChannelHandlerContext): Unit = {
      val channel = ctx.channel()
      if (writeBuffer.nonEmpty && channel.isActive && channel.isWritable) {
        val combiner = new PromiseCombiner
        do {
          val p = ctx.newPromise()
          ctx.write(writeBuffer.dequeue(), p)
          combiner.add(p)
        } while (writeBuffer.nonEmpty && channel.isActive && channel.isWritable)

        if (channel.isActive) {
          val p = ctx.newPromise()
          if (writeBuffer.isEmpty)
            p.addListener(new ChannelFutureListener {
              override def operationComplete(f: ChannelFuture): Unit = {
                if (f.isSuccess) writePromise.trySuccess()
                else writePromise.tryFailure(f.cause())
                resetWriteBuffer()
              }
            })
          else
            p.addListener(new ChannelFutureListener {
              override def operationComplete(f: ChannelFuture): Unit = {
                if (f.isSuccess) {
                  if (channel.isWritable) flushWriteBuffer(ctx)
                } else discardWriteBuffer(f.cause())
              }
            })

          combiner.finish(p)
          ctx.flush()
        } else discardWriteBuffer()
      }
    }

    /**
      * Fails pending write, if any.
      */
    private def discardWriteBuffer(cause: Throwable = new ClosedChannelException): Unit =
      if (writePending) {
        writePromise.tryFailure(cause)
        resetWriteBuffer()
      }

    override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
      if (ctx.channel().isWritable)
        if (writePending) flushWriteBuffer(ctx) else pullInput(())
      ctx.fireChannelWritabilityChanged()
    }

    private val classO = classTag[O].runtimeClass

    /**
      * Elements received from the channel but not yet dispatched to the
      * stage thread. Accessed from the I/O thread.
      */
    private val outputBuffer = new mutable.ListBuffer[O]

    /**
      * Indicates that the downstream has requested more data but the
      * output buffer was empty at that time. Accessed from the I/O thread.
      */
    private var demand = false

    /**
      * Indicates that the remote peer has half-closed the channel.
      * Accessed from the I/O thread.
      */
    private var readClosed = false

    /**
      * Adds received elements to the output buffer. If the buffer reaches
      * outputBufferMaxSize, all additional elements are dropped until the
      * buffer is flushed.
      */
    override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit =
      if (classO.isInstance(msg)) {
        if (outputBuffer.size < outputBufferMaxSize)
          outputBuffer += msg.asInstanceOf[O]
      } else ctx.fireChannelRead(msg)

    /**
      * Flushes the output buffer after the downstream has signalled demand.
      * If the downstream cannot keep up with the transfer rate and the buffer
      * contains more than outputBufferSoftLimit elements, it tries to slow
      * down the channel temporarily.
      */
    override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
      val bufferSize = outputBuffer.size
      if (demand && bufferSize > 0) flushOutputBuffer(ctx)
      val autoRead = bufferSize <= outputBufferSoftLimit
      if (autoRead != isAutoRead(ctx)) setAutoRead(ctx, autoRead)
      ctx.fireChannelReadComplete()
    }

    /**
      * Dispatches buffered output elements to the stage thread so that
      * they can be emitted through the output port. If the read side
      * has already been closed it also signals that the output port
      * should be closed. If the channel is closed it signals that the stage
      * should be completed.
      */
    private def flushOutputBuffer(ctx: ChannelHandlerContext): Unit = {
      demand = false
      val tmp = outputBuffer.toList
      outputBuffer.clear()
      if (!ctx.channel().isOpen) pushOutputAndComplete(tmp)
      else if (readClosed) pushOutputAndCloseOutput(tmp)
      else pushOutput(tmp)
    }

    private def isAutoRead(ctx: ChannelHandlerContext): Boolean =
      ctx.channel().config().isAutoRead

    private def setAutoRead(ctx: ChannelHandlerContext, autoRead: Boolean): Unit =
      ctx.channel().config().setAutoRead(autoRead)

    override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
      if (evt.isInstanceOf[ChannelInputShutdownEvent]) {
        readClosed = true
        flushOutputBuffer(ctx)
      }
      ctx.fireUserEventTriggered(evt)
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      discardWriteBuffer()
      flushOutputBuffer(ctx)
      ctx.fireChannelInactive()
    }

    override def handlerRemoved(ctx: ChannelHandlerContext): Unit =
      if (ctx.channel().isActive) throw new IllegalStateException(
        "Cannot remove the bridge while the channel is active")

    /**
      * Signals that the output port has requested more data. It either
      * flushes the output buffer if non-empty or resumes transfer. Can
      * be safely invoked from any thread, the operation always runs
      * on the I/O thread.
      */
    def addDemand(): Unit = ctx.channel().eventLoop().execute(
      new Runnable {
        override def run(): Unit = {
          if (outputBuffer.nonEmpty) flushOutputBuffer(ctx)
          else if (ctx.channel().isActive && !readClosed) {
            demand = true
            if (!isAutoRead(ctx)) ctx.read()
          }
        }
      })
  }

  // Set an initial handler for the output port. It is used until the bridge
  // is ready, i.e. the channel is active and the bridge is added to the
  // channel's pipeline. It ignores pull requests and/or completes the stage
  // when the downstream cancels.
  setHandler(shape.out, new OutHandler {
    override def onPull(): Unit = ()
    override def onDownstreamFinish(): Unit = cancel()
  })

  /**
    * Cancels the input port, closes the channel and eventually completes
    * the stage after the downstream cancels. Invoked from the stage thread.
    */
  private def cancel(): Unit = {
    cancel(shape.in)
    channel.close()
  }

  /**
    * Replaces the initial handler for the output port and starts the
    * transfer after the bridge becomes active, i.e. the channel is active
    * and the bridge is added to the channel's pipeline. It delegates pull
    * requests to the bridge and/or completes the stage when the downstream
    * cancels.
    */
  private val bridgeReady = getAsyncCallback[Bridge] { bridge ⇒
    connecting = false
    setHandler(shape.out, new OutHandler {
      override def onPull(): Unit = bridge.addDemand()
      override def onDownstreamFinish(): Unit = cancel()
    })
    writeOrPull()
    if (isAvailable(shape.out)) bridge.addDemand()
  } invoke _

  /**
    * Emits received data through the output port.
    */
  private val pushOutput = getAsyncCallback[List[O]] {
    emitMultiple(shape.out, _)
  } invoke _

  /**
    * Emits received data through the output port and closes it.
    */
  private val pushOutputAndCloseOutput = getAsyncCallback[List[O]] {
    emitMultiple(shape.out, _, () ⇒ closeOutput())
  } invoke _

  /**
    * Closes the output port after the read side of the channel is closed
    * and all remaining received elements are emitted. If the input port has
    * already been closed, it also closes the channel and eventually completes
    * the stage because no more elements can be processed. Invoked from
    * the stage thread.
    */
  private def closeOutput(): Unit = {
    complete(shape.out)
    if (isClosed(shape.in)) channel.close()
  }

  /**
    * Emits received data through the output port and completes the stage.
    */
  private val pushOutputAndComplete = getAsyncCallback[List[O]] {
    emitMultiple(shape.out, _, () ⇒ completeStage())
  } invoke _
}
