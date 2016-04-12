package reactivehub.akka.stream.apns.helper

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.channel.socket.{ChannelInputShutdownEvent, SocketChannel}

final class EchoServer(group: NioEventLoopGroup, halfClose: Boolean = true)
    extends AbstractServer[NioServerSocketChannel] {

  override protected def createChannelSync(): NioServerSocketChannel =
    new ServerBootstrap()
      .group(group)
      .channel(classOf[NioServerSocketChannel])
      .childOption(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.box(halfClose))
      .handler(new ChannelInitializer[NioServerSocketChannel] {
        override def initChannel(channel: NioServerSocketChannel): Unit =
          channel.pipeline().addLast(createAddToChannelGroupHandler())
      })
      .childHandler(new ChannelInitializer[NioSocketChannel] {
        override def initChannel(channel: NioSocketChannel): Unit = {
          val pipeline = channel.pipeline()
          pipeline.addLast(createAddToChannelGroupHandler())
          pipeline.addLast(new ChannelInboundHandlerAdapter {
            override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit =
              ctx.write(msg)

            override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
              ctx.flush()

            override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
              ctx.channel().config().setAutoRead(ctx.channel().isWritable)
              ctx.fireChannelWritabilityChanged()
            }

            override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit =
              if (evt.isInstanceOf[ChannelInputShutdownEvent])
                ctx.channel().asInstanceOf[SocketChannel].shutdownOutput()
          })
        }
      }).bind("localhost", 0).sync().channel().asInstanceOf[NioServerSocketChannel]
}
