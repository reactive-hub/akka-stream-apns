package reactivehub.akka.stream.apns.marshallers

import net.liftweb.json.{parse ⇒ liftParse, _}
import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.marshallers.MarshallerBehaviours.Custom

class LiftJsonPayloadMarshallerSpec extends FlatSpec with Matchers
    with MarshallerBehaviours with LiftJsonSupport {

  override val m = LiftJsonPayloadMarshaller
  override def wrap(field: String, value: JValue): JValue = JObject(List(JField(field, value)))
  override def parse(str: String): JValue = liftParse(str)

  implicit val _ = DefaultFormats

  val t = Custom("test", 123)
  val expectedT = parse(
    """
      |{
      |  "field1": "test",
      |  "field2": 123
      |}
    """.stripMargin)

  "LiftJsonPayloadMarshaller" should behave like payloadMarshaller("test", 123, t, expectedT)
  "ListJsonResponseUnmarshaller" should behave like responseUnmarshaller
}
