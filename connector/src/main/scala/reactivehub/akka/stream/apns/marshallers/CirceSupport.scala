package reactivehub.akka.stream.apns.marshallers

import akka.util.ByteString
import io.circe._
import reactivehub.akka.stream.apns.PayloadMarshaller

trait CirceSupport {
  implicit object CircePayloadMarshaller extends PayloadMarshaller {
    override type Node = Json
    override type Writer[T] = Encoder[T]
    override def jsonString(value: String): Json = Json.string(value)
    override def jsonNumber(value: Int): Json = Json.int(value)
    override def jsonArray(elements: Seq[Json]): Json = Json.array(elements: _*)
    override def jsonObject(fields: Map[String, Json]): Json = Json.obj(fields.toSeq: _*)
    override def write[T](t: T, w: Encoder[T]): Json = w(t)
    override def print(value: Json): ByteString = ByteString(Printer.noSpaces.pretty(value))
  }
}
