package reactivehub.akka.stream.apns.helper

import akka.stream._
import akka.stream.scaladsl.Keep
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.testkit.TestPublisher.{Probe ⇒ TPP}
import akka.stream.testkit.TestSubscriber.{OnError, Probe ⇒ TSP}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKitBase, TestProbe}
import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.buffer.{ByteBufAllocator, UnpooledByteBufAllocator}
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.socket.{ChannelInputShutdownEvent, SocketChannel}
import java.net.InetSocketAddress
import reactivehub.akka.stream.apns.NettyLogic
import reactivehub.akka.stream.apns.helper.NettyHelpers._
import scala.reflect.ClassTag

object NettyHelpers {
  sealed trait ChannelEvent

  case object Connected extends ChannelEvent
  case object ConnectFailed extends ChannelEvent
  case object Closed extends ChannelEvent
  case object InputClosed extends ChannelEvent

  case object ChannelReady
  final case class Received(data: Any)
}

trait NettyHelpers {
  this: TestKitBase ⇒

  abstract class TestableNettyStage[I, O: ClassTag]
      extends GraphStage[FlowShape[I, O]] { self ⇒

    val in = Inlet[I]("NettyStage.in")
    val out = Outlet[O]("NettyStage.out")

    override val shape = FlowShape(in, out)

    private val probe = TestProbe()

    def expectConnected() = probe.expectMsg(Connected)
    def expectConnectFailed() = probe.expectMsg(ConnectFailed)
    def expectClosed() = probe.expectMsg(Closed)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new NettyLogic(shape) {
        override protected def createChannel(bridge: ChannelHandler): ChannelFuture = {
          val future = self.createChannel(bridge)
          future.addListener(new ChannelFutureListener {
            override def operationComplete(f: ChannelFuture): Unit =
              if (f.isSuccess) probe.ref ! Connected
              else probe.ref ! ConnectFailed
          })
          future.channel().closeFuture().addListener(new ChannelFutureListener {
            override def operationComplete(f: ChannelFuture): Unit = {
              probe.ref ! Closed
            }
          })
          future
        }
      }

    protected def createChannel(bridge: ChannelHandler): ChannelFuture
  }

  abstract class TestableSocketNettyStage[I, O: ClassTag](
    remoteAddress: InetSocketAddress,
    group: NioEventLoopGroup,
    halfClose: Boolean = true,
    allocator: ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT)
      extends TestableNettyStage[I, O] {

    override def createChannel(bridge: ChannelHandler): ChannelFuture =
      new Bootstrap()
        .group(group)
        .channel(classOf[NioSocketChannel])
        .option(ChannelOption.ALLOCATOR, allocator)
        .option(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.box(halfClose))
        .handler(new ChannelInitializer[SocketChannel] {
          override def initChannel(channel: SocketChannel): Unit =
            setup(channel.pipeline(), bridge)
        })
        .connect(remoteAddress)

    protected def setup(pipeline: ChannelPipeline, bridge: ChannelHandler): Unit
  }

  abstract class TestableServer[T: ClassTag](
    group: NioEventLoopGroup,
    channelReadyWhenActive: Boolean = true,
    halfClose: Boolean = true,
    allocator: ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT)
      extends AbstractServer[NioServerSocketChannel] {

    private val serverProbe = TestProbe()

    override protected def createChannelSync(): NioServerSocketChannel =
      new ServerBootstrap()
        .group(group)
        .channel(classOf[NioServerSocketChannel])
        .option(ChannelOption.ALLOCATOR, allocator)
        .childOption(ChannelOption.ALLOCATOR, allocator)
        .childOption(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.box(halfClose))
        .handler(new ChannelInitializer[NioServerSocketChannel] {
          override def initChannel(channel: NioServerSocketChannel): Unit =
            channel.pipeline().addLast(createAddToChannelGroupHandler())
        })
        .childHandler(new ChannelInitializer[NioSocketChannel] {
          override def initChannel(channel: NioSocketChannel): Unit = {
            val probeHandler = new ChannelInboundHandlerAdapter {
              private val probe = TestProbe()
              private val dataProbe = TestProbe()

              override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
                if (ctx.channel().isActive && channelReadyWhenActive)
                  ctx.pipeline().fireUserEventTriggered(ChannelReady)
                super.handlerAdded(ctx)
              }

              override def channelActive(ctx: ChannelHandlerContext): Unit = {
                if (channelReadyWhenActive)
                  ctx.pipeline().fireUserEventTriggered(ChannelReady)
                super.channelActive(ctx)
              }

              override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
                evt match {
                  case ChannelReady ⇒
                    val c = createConnection(ctx.channel(), probe, dataProbe)
                    serverProbe.ref ! c

                  case Received(data)               ⇒ dataProbe.ref ! data
                  case _: ChannelEvent              ⇒ probe.ref ! evt
                  case _: ChannelInputShutdownEvent ⇒ probe.ref ! InputClosed
                  case _                            ⇒
                }
                super.userEventTriggered(ctx, evt)
              }
            }

            val pipeline = channel.pipeline()
            setup(pipeline)
            pipeline.addFirst(probeHandler)
            pipeline.addFirst(createAddToChannelGroupHandler())
          }
        }).bind("localhost", 0).sync().channel().asInstanceOf[NioServerSocketChannel]

    protected def setup(pipeline: ChannelPipeline): Unit

    protected def createConnection(channel: Channel, probe: TestProbe,
      dataProbe: TestProbe): T

    def expectConnection(): T = serverProbe.expectMsgType[T]
  }

  def run[I, O](graph: Graph[FlowShape[I, O], _])(implicit m: Materializer): (TPP[I], TSP[O]) =
    TestSource.probe[I]
      .viaMat(graph.async)(Keep.left)
      .toMat(TestSink.probe[O])(Keep.both)
      .run()

  def runMat[I, O, M](graph: Graph[FlowShape[I, O], M])(implicit m: Materializer): (TPP[I], TSP[O], M) =
    TestSource.probe[I]
      .viaMat(graph)(Keep.both)
      .toMat(TestSink.probe[O])((l, r) ⇒ (l._1, r, l._2))
      .run()

  implicit class PimpedTestSubscriberProbe(tsp: TSP[_]) {
    def expectErrorType[T <: Throwable](implicit ct: ClassTag[T]): T =
      tsp.expectEventPF {
        case OnError(cause) if ct.runtimeClass.isInstance(cause) ⇒ cause.asInstanceOf[T]
      }
  }
}
