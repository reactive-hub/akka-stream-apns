package reactivehub.akka.stream.apns

import akka.util.ByteString
import spray.json._

trait SprayJsonSupport {
  implicit object SprayJsonPayloadMarshaller extends PayloadMarshaller {
    override type Node = JsValue
    override type Writer[T] = JsonWriter[T]
    override def jsonString(value: String): JsValue = JsString(value)
    override def jsonNumber(value: Int): JsValue = JsNumber(value)
    override def jsonArray(elements: Seq[JsValue]): JsValue = JsArray(elements: _*)
    override def jsonObject(fields: Map[String, JsValue]): JsValue = JsObject(fields)
    override def write[T](t: T, w: JsonWriter[T]): JsValue = w.write(t)
    override def print(value: JsValue): ByteString = ByteString(CompactPrinter(value))
  }
}
