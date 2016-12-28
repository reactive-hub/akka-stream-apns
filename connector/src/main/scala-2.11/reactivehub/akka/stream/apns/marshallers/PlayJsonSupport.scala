package reactivehub.akka.stream.apns.marshallers

import akka.util.ByteString
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import reactivehub.akka.stream.apns.ResponseUtil._
import reactivehub.akka.stream.apns._

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

  private[apns] object ResponseUnmarshallerReads {
    implicit object ReasonReads extends Reads[Reason] {
      override def reads(json: JsValue): JsResult[Reason] = json match {
        case JsString(str) ⇒ parseReason(str).map(JsSuccess(_))
          .getOrElse(JsError("Cannot read Reason"))
        case _ ⇒ JsError("Cannot read Reason")
      }
    }

    implicit val residentReads: Reads[ResponseBody] = (
      (JsPath \ "reason").read[Reason] and
      (JsPath \ "timestamp").readNullable[Long])(ResponseBody.apply _)
  }

  implicit object PlayJsonResponseUnmarshaller extends ResponseUnmarshaller {
    import ResponseUnmarshallerReads._
    override def read(str: String): ResponseBody =
      Json.parse(str).validate[ResponseBody].asOpt.get
  }
}
