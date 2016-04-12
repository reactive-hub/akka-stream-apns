package reactivehub.akka.stream.apns.marshallers

import akka.util.ByteString
import net.liftweb.json._
import reactivehub.akka.stream.apns.ResponseUtil.parseReason
import reactivehub.akka.stream.apns._

trait LiftJsonSupport {
  import LiftJsonSupport.LiftWriter

  implicit object LiftJsonPayloadMarshaller extends PayloadMarshaller {
    override type Node = JValue
    override type Writer[T] = LiftWriter[T]
    override def jsonString(value: String): JValue = JString(value)
    override def jsonNumber(value: Int): JValue = JInt(value)
    override def jsonArray(elements: Seq[JValue]): JValue = JArray(elements.toList)
    override def jsonObject(fields: Map[String, JValue]): JValue = JObject(fields.toList.map(kv ⇒ JField(kv._1, kv._2)))
    override def write[T](t: T, w: LiftWriter[T]): JValue = w.write(t)
    override def print(value: JValue): ByteString = ByteString(compact(render(value)))
  }

  implicit def liftWriter[T](implicit f: Formats): LiftWriter[T] = new LiftWriter[T]

  private[apns] object ResponseUnmarshallerFormats {
    implicit val Format = DefaultFormats +
      new CustomSerializer[Reason]({ ser ⇒
        ({
          case json @ JString(str) ⇒
            parseReason(str).getOrElse(
              throw new MappingException(s"Can't convert $json to Reason"))
        }, PartialFunction.empty)
      })
  }

  implicit object LiftJsonResponseUnmarshaller extends ResponseUnmarshaller {
    import ResponseUnmarshallerFormats._
    override def read(str: String): ResponseBody = parse(str).extract[ResponseBody]
  }
}

object LiftJsonSupport {
  final class LiftWriter[T](implicit f: Formats) {
    def write(t: T): JValue = Extraction.decompose(t)
  }
}
