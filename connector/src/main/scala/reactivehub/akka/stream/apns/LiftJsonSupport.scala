package reactivehub.akka.stream.apns

import akka.util.ByteString
import net.liftweb.json._
import scala.language.implicitConversions

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
}

object LiftJsonSupport {
  class LiftWriter[T](implicit f: Formats) {
    def write(t: T): JValue = Extraction.decompose(t)
  }
}
