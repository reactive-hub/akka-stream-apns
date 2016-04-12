package reactivehub.akka.stream.apns.marshallers

import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns._

trait MarshallerBehaviours {
  this: FlatSpec with Matchers ⇒

  val m: PayloadMarshaller
  def wrap(field: String, value: m.Node): m.Node
  def parse(value: String): m.Node

  def payloadMarshaller[T](string: String, int: Int, t: T, expectedT: m.Node)(implicit w: m.Writer[T]): Unit = {
    it should "encode a String" in {
      val encoded = wrap("field", m.jsonString(string))
      val expected = parse(
        s"""
           |{
           |  "field": "$string"
           |}
        """.stripMargin)
      encoded should be(expected)
    }

    it should "encode an Int" in {
      val encoded = wrap("field", m.jsonNumber(int))
      val expected = parse(
        s"""
           |{
           |  "field": $int
           |}
        """.stripMargin)
      encoded should be(expected)
    }

    it should "encode an Array" in {
      val encoded = m.jsonArray(Seq(m.jsonString(string), m.jsonNumber(int)))
      val expected = parse(
        s"""
           |["$string", $int]
         """.stripMargin)
      encoded should be(expected)
    }

    it should "encode an Object" in {
      val encoded = m.jsonObject(
        Map(
          "field1" → m.jsonString(string),
          "field2" → m.jsonNumber(int),
          "field3" → m.jsonArray(Seq(m.jsonString(string), m.jsonNumber(int))),
          "field4" → m.jsonObject(Map("field" → m.jsonString(string)))))
      val expected = parse(
        s"""
           |{
           |  "field1": "$string",
           |  "field2": $int,
           |  "field3": ["$string", $int],
           |  "field4": {
           |    "field": "$string"
           |  }
           |}
        """.stripMargin)
      encoded should be(expected)
    }

    it should "encode a custom object" in {
      val encoded = m.write(t, w)
      encoded should be(expectedT)
    }

    it should "render a valid JSON" in {
      val encoded = m.jsonObject(
        Map(
          "field1" → m.jsonString(string),
          "field2" → m.jsonNumber(int),
          "field3" → m.jsonArray(Seq(m.jsonString(string), m.jsonNumber(int))),
          "field4" → m.jsonObject(Map("field" → m.jsonString(string)))))
      val expected = parse(m.print(encoded).utf8String)
      encoded should be(expected)
    }
  }

  def responseUnmarshaller(implicit ru: ResponseUnmarshaller): Unit = {
    it should "read a valid JSON with key reason" in {
      val json =
        s"""
           |{
           |  "reason": "PayloadEmpty"
           |}
         """.stripMargin
      ru.read(json) shouldBe ResponseBody(Reason.PayloadEmpty, None)
    }

    it should "read a valid JSON with keys reason and timestamp" in {
      val json =
        s"""
           |{
           |  "reason": "PayloadEmpty",
           |  "timestamp": 12345
           |}
         """.stripMargin
      ru.read(json) shouldBe ResponseBody(Reason.PayloadEmpty, Some(12345L))
    }

    it should "fail to read a valid JSON with no reason key" in {
      val json =
        s"""
           |{
           |  "timestamp": 12345
           |}
         """.stripMargin
      intercept[Exception] {
        ru.read(json)
      }
    }

    it should "fail to read a valid JSON with an unknown reason" in {
      val json =
        s"""
           |{
           |  "reason": "UnknownReason"
           |}
         """.stripMargin
      intercept[Exception] {
        ru.read(json)
      }
    }

    it should "fail to read an invalid JSON" in {
      intercept[Exception] {
        ru.read("invalid")
      }
    }
  }

  def responseUnmarshallerWithSaneNone(implicit ru: ResponseUnmarshaller): Unit = {
    responseUnmarshaller

    it should "fail to read a valid JSON with invalid timestamp" in {
      val json =
        s"""
           |{
           |  "reason": "PayloadEmpty",
           |  "timestamp": "string"
           |}
         """.stripMargin
      intercept[Exception] {
        ru.read(json)
      }
    }
  }
}

object MarshallerBehaviours {
  case class Custom(field1: String, field2: Int)
}
