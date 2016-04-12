package reactivehub.akka.stream.apns.marshallers

import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser
import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.marshallers.MarshallerBehaviours.Custom

class CircePayloadMarshallerSpec extends FlatSpec with Matchers
    with MarshallerBehaviours with CirceSupport {

  override val m = CircePayloadMarshaller
  override def wrap(field: String, value: Json): Json = Json.obj(field → value)
  override def parse(value: String): Json = parser.parse(value).toOption.get

  val t = Custom("test", 123)
  val expectedT = parse(
    """
      |{
      |  "field1": "test",
      |  "field2": 123
      |}
    """.stripMargin)

  "CircePayloadMarshaller" should behave like payloadMarshaller("test", 123, t, expectedT)
  "CirceResponseUnmarshaller" should behave like responseUnmarshallerWithSaneNone
}
