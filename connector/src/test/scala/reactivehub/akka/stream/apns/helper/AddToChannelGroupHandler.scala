package reactivehub.akka.stream.apns.helper

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.channel.group.ChannelGroup

final class AddToChannelGroupHandler(group: ChannelGroup)
    extends ChannelInboundHandlerAdapter {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    group.add(ctx.channel())
    ctx.channel().pipeline().remove(this)
    ctx.fireChannelActive()
  }
}
