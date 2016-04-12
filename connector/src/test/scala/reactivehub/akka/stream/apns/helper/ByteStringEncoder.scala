package reactivehub.akka.stream.apns.helper

import akka.util.ByteString
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import java.util.{List ⇒ JList}

final class ByteStringEncoder extends MessageToMessageEncoder[ByteString] {
  override def encode(ctx: ChannelHandlerContext, msg: ByteString,
    out: JList[AnyRef]): Unit =
    out.add(ctx.alloc().buffer(msg.length).writeBytes(msg.toArray))
}
