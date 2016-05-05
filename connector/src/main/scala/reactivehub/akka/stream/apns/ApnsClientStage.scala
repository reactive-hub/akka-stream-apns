package reactivehub.akka.stream.apns

import akka.stream._
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue}
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.ssl._
import java.lang.{Class ⇒ JClass}
import java.net.InetSocketAddress
import scala.concurrent.{Future, Promise}
import scala.reflect.{ClassTag, classTag}

/**
  * A flow-shaped GraphStage built around NettyLogic. On startup it establishes
  * a new HTTP/2 connection to APNs and adds ApnsConnectionHandler to its
  * pipeline.
  *
  * ApnsClientStage consumes messages of type (T, Notification), sends
  * notifications via the HTTP/2 channel to APNs, receives corresponding
  * responses and produces messages of type (T, Response). Correlation ids T
  * associate notifications with their responses.
  *
  * ApnsClientStage materializes a value of type Future[Option[Reason]]. When
  * the connection is terminated by the remote peer, it completes with the
  * reason sent with the GoAway HTTP/2 frame. Otherwise it completes with None
  * when the stage terminates.
  */
final class ApnsClientStage[T, C <: SocketChannel: ClassTag](
  remoteAddress: InetSocketAddress, sslContext: SslContext,
  group: EventLoopGroup)(implicit ru: ResponseUnmarshaller)
    extends GraphStageWithMaterializedValue[FlowShape[(T, Notification), (T, Response)], Future[Option[Reason]]] {

  val in = Inlet[(T, Notification)]("ApnsClient.in")
  val out = Outlet[(T, Response)]("ApnsClient.out")

  override val shape = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Option[Reason]]) = {
    val reasonPromise = Promise[Option[Reason]]()
    val logic = new NettyLogic(shape) {
      override protected def createChannel(bridge: ChannelHandler): ChannelFuture =
        new Bootstrap()
          .group(group)
          .channel(classTag[C].runtimeClass.asInstanceOf[JClass[C]])
          .option(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.box(false))
          .option(ChannelOption.SO_KEEPALIVE, Boolean.box(true))
          .handler(new Initializer(bridge, reasonPromise))
          .connect(remoteAddress)

      override def postStop(): Unit = reasonPromise.trySuccess(None)
    }
    (logic, reasonPromise.future)
  }

  class Initializer(bridge: ChannelHandler, promise: Promise[Option[Reason]])
      extends ChannelInitializer[SocketChannel] {

    override def initChannel(channel: SocketChannel): Unit = {
      val pipeline = channel.pipeline()
      pipeline.addLast(sslContext.newHandler(channel.alloc()))
      pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
        override def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit =
          if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            val pipeline = ctx.channel().pipeline()

            val settingsPromise = ctx.channel().newPromise()
            settingsPromise.addListener(new ChannelFutureListener {
              override def operationComplete(f: ChannelFuture): Unit =
                pipeline.addLast(bridge)
            })

            pipeline.addLast(apnsConnectionHandler)
            pipeline.addLast(new Http2SettingsHandler(settingsPromise))
          } else throw new IllegalStateException(s"Unexpected protocol $protocol")
      })
    }

    def apnsConnectionHandler: ChannelHandler =
      ApnsConnectionHandler.Builder()
        .reasonPromise(promise)
        .propagateSettings(true)
        .responseUnmarshaller(ru)
        .build()
  }

  class Http2SettingsHandler(promise: ChannelPromise)
      extends SimpleChannelInboundHandler[Http2Settings] {

    override def channelRead0(ctx: ChannelHandlerContext, msg: Http2Settings): Unit = {
      promise.setSuccess()
      ctx.pipeline().remove(this)
    }
  }
}

object NioApnsClientStage {
  def apply[T](remoteAddress: InetSocketAddress, sslContext: SslContext,
    group: NioEventLoopGroup)(implicit ru: ResponseUnmarshaller): ApnsClientStage[T, NioSocketChannel] =
    new ApnsClientStage[T, NioSocketChannel](remoteAddress, sslContext, group)
}
