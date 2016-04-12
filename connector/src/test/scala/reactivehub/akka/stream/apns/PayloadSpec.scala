package reactivehub.akka.stream.apns

import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.Payload.Builder
import reactivehub.akka.stream.apns.marshallers.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json._

class PayloadSpec
    extends FlatSpec
    with Matchers
    with SprayJsonSupport
    with DefaultJsonProtocol {

  case class Custom(fld1: String, fld2: Int)
  implicit val CustomFmt = jsonFormat2(Custom)

  def parse(payload: Payload): JsValue = payload.value.utf8String.parseJson

  "Payload.empty" should "be {apns:{}}" in {
    val expected =
      """
        |{
        |  "aps": {}
        |}
      """.stripMargin.parseJson

    parse(Payload.empty) shouldBe expected
  }

  "Builder" should "be able to build an empty payload" in {
    parse(Builder().result) shouldBe parse(Payload.empty)
  }

  it should "be able to build a simple payload" in {
    val payload = Builder()
      .withAlert("alert")
      .withBadge(10)
      .withSound("sound")
      .withContentAvailable
      .withCategory("category")
      .withCustomField("custom", Custom("val1", 2))
      .result

    val expected =
      """
        |{
        |  "aps": {
        |    "alert": "alert",
        |    "badge": 10,
        |    "sound": "sound",
        |    "content-available": 1,
        |    "category": "category"
        |  },
        |  "custom": {
        |    "fld1": "val1",
        |    "fld2": 2
        |  }
        |}
      """.stripMargin.parseJson

    parse(payload) shouldBe expected
  }

  it should "be able to build a complex payload" in {
    val payload = Builder()
      .withAlert("alert")
      .withBadge(10)
      .withSound("sound")
      .withContentAvailable
      .withCategory("category")
      .withCustomField("custom", Custom("val1", 2))
      .withTitle("title")
      .withLocalizedTitle("title_key", "arg1", "arg2")
      .withLocalizedAction("action_key")
      .withLocalizedAlert("alert_key")
      .withLaunchImage("launchImage")
      .result

    val expected =
      """
        |{
        |  "aps": {
        |    "alert": {
        |      "title": "title",
        |      "body": "alert",
        |      "title-loc-key": "title_key",
        |      "title-loc-args": ["arg1", "arg2"],
        |      "action-loc-key": "action_key",
        |      "loc-key": "alert_key",
        |      "loc-args": [],
        |      "launch-image": "launchImage"
        |    },
        |    "badge": 10,
        |    "sound": "sound",
        |    "content-available": 1,
        |    "category": "category"
        |  },
        |  "custom": {
        |    "fld1": "val1",
        |    "fld2": 2
        |  }
        |}
      """.stripMargin.parseJson

    parse(payload) shouldBe expected
  }
}
