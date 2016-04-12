package reactivehub.akka.stream.apns.marshallers

import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsValue, Json}
import reactivehub.akka.stream.apns.marshallers.MarshallerBehaviours.Custom

class PlayJsonPayloadMarshallerSpec extends FlatSpec with Matchers
    with MarshallerBehaviours with PlayJsonSupport {

  override val m = PlayJsonPayloadMarshaller
  override def wrap(field: String, value: JsValue): JsValue = Json.obj(field → value)
  override def parse(value: String): JsValue = Json.parse(value)

  implicit val _ = Json.format[Custom]

  val t = Custom("test", 123)
  val expectedT = parse(
    """
      |{
      |  "field1": "test",
      |  "field2": 123
      |}
    """.stripMargin)

  "PlayJsonPayloadMarshaller" should behave like payloadMarshaller("test", 123, t, expectedT)
  "PlayJsonResponseUnmarshaller" should behave like responseUnmarshallerWithSaneNone
}
