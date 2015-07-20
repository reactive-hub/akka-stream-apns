package reactivehub.akka.stream.apns

import akka.util.ByteString
import play.api.libs.json._

trait PlayJsonSupport {
  implicit object PlayJsonPayloadMarshaller extends PayloadMarshaller {
    override type Node = JsValue
    override type Writer[T] = Writes[T]
    override def jsonString(value: String): JsValue = JsString(value)
    override def jsonNumber(value: Int): JsValue = JsNumber(value)
    override def jsonArray(elements: Seq[JsValue]): JsValue = JsArray(elements)
    override def jsonObject(fields: Map[String, JsValue]): JsValue = JsObject(fields.toSeq)
    override def write[T](t: T, w: Writes[T]): JsValue = w.writes(t)
    override def print(value: JsValue): ByteString = ByteString(Json.stringify(value))
  }
}
