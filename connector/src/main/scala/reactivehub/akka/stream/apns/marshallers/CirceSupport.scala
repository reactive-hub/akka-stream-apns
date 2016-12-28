package reactivehub.akka.stream.apns.marshallers

import akka.util.ByteString
import io.circe.Decoder.instance
import io.circe._
import io.circe.parser.parse
import reactivehub.akka.stream.apns.ResponseUtil._
import reactivehub.akka.stream.apns._

trait CirceSupport {
  implicit object CircePayloadMarshaller extends PayloadMarshaller {
    override type Node = Json
    override type Writer[T] = Encoder[T]
    override def jsonString(value: String): Json = Json.fromString(value)
    override def jsonNumber(value: Int): Json = Json.fromInt(value)
    override def jsonArray(elements: Seq[Json]): Json = Json.arr(elements: _*)
    override def jsonObject(fields: Map[String, Json]): Json = Json.obj(fields.toSeq: _*)
    override def write[T](t: T, w: Encoder[T]): Json = w(t)
    override def print(value: Json): ByteString = ByteString(Printer.noSpaces.pretty(value))
  }

  private[apns] object ResponseBodyDecoders {
    implicit val ReasonDecoder = instance { c ⇒
      for {
        s ← c.focus.as[String].right
        r ← parseReason(s).toRight(DecodingFailure("Reason", c.history)).right
      } yield r
    }

    implicit val ResponseBodyDecoder = instance { c ⇒
      for {
        r ← c.downField("reason").as[Reason].right
        t ← c.downField("timestamp").as[Option[Long]].right
      } yield ResponseBody(r, t)
    }
  }

  implicit object CirceResponseUnmarshaller extends ResponseUnmarshaller {
    import ResponseBodyDecoders._
    override def read(str: String): ResponseBody =
      parse(str).right.flatMap(Decoder[ResponseBody].decodeJson).right.get
  }
}
