package reactivehub.akka.stream.apns

import org.scalatest.{ FlatSpec, Matchers }
import reactivehub.akka.stream.apns.MarshallerBehaviours.Custom
import spray.json.DefaultJsonProtocol._
import spray.json._

class SprayJsonPayloadMarshallerSpec extends FlatSpec with Matchers with MarshallerBehaviours with SprayJsonSupport {
  override val m = SprayJsonPayloadMarshaller
  override def wrap(field: String, value: JsValue): JsValue = JsObject(field -> value)
  override def parse(value: String): JsValue = value.parseJson

  implicit val _ = jsonFormat2(Custom)

  val t = Custom("test", 123)
  val expectedT = parse(
    """
      |{
      |  "field1": "test",
      |  "field2": 123
      |}
    """.stripMargin)

  "SprayJsonPayloadMarshaller" should behave like payloadMarshaller("test", 123, t, expectedT)
}
