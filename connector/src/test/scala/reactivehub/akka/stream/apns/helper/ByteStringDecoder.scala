package reactivehub.akka.stream.apns.helper

import akka.util.ByteString
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import java.util.{List ⇒ JList}

final class ByteStringDecoder extends MessageToMessageDecoder[ByteBuf] {
  override def decode(ctx: ChannelHandlerContext, msg: ByteBuf,
    out: JList[AnyRef]): Unit = {
    val length = msg.readableBytes()
    if (msg.hasArray)
      out.add(ByteString.fromArray(msg.array(), msg.arrayOffset(), length))
    else {
      val dst = Array.ofDim[Byte](length)
      msg.getBytes(msg.readerIndex(), dst)
      out.add(ByteString(dst))
    }
  }
}
