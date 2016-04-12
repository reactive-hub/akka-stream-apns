package reactivehub.akka.stream.apns.helper

import io.netty.channel._
import io.netty.channel.group.{ChannelGroupFuture, DefaultChannelGroup}
import io.netty.util.concurrent.GlobalEventExecutor
import java.net.InetSocketAddress

abstract class AbstractServer[C <: ServerChannel] {
  private val channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)

  protected def createAddToChannelGroupHandler(): AddToChannelGroupHandler =
    new AddToChannelGroupHandler(channelGroup)

  private val _channel = createChannelSync()

  protected def createChannelSync(): C

  protected def channel: C = _channel

  def address: InetSocketAddress =
    channel.localAddress().asInstanceOf[InetSocketAddress]

  def close(): ChannelGroupFuture = channelGroup.close()

  def closeSync(): Unit = channelGroup.close().sync()
}
