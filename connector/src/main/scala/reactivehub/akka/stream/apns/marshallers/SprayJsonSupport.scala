package reactivehub.akka.stream.apns.marshallers

import akka.util.ByteString
import reactivehub.akka.stream.apns.ResponseUtil.parseReason
import reactivehub.akka.stream.apns._
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

  private[apns] object ResponseBodyFormats extends DefaultJsonProtocol {
    implicit val ReasonFmt = lift(new JsonReader[Reason] {
      override def read(json: JsValue): Reason = json match {
        case JsString(string) ⇒ parseReason(string).getOrElse(error(json))
        case _                ⇒ error(json)
      }

      def error(json: JsValue) = deserializationError("Cannot read Reason")
    })

    implicit val ResponseBodyFmt = jsonFormat2(ResponseBody)
  }

  implicit object SprayJsonResponseUnmarshaller extends ResponseUnmarshaller {
    import ResponseBodyFormats._
    override def read(str: String): ResponseBody =
      ResponseBodyFmt.read(str.parseJson)
  }
}
