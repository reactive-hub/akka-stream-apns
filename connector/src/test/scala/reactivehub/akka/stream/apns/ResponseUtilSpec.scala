package reactivehub.akka.stream.apns

import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.ResponseUtil.{parseReason, parseStatusCode}

class ResponseUtilSpec extends FlatSpec with Matchers {
  "Method parseReason" should "convert string \"PayloadEmpty\" to Reason.PayloadEmpty" in {
    parseReason("PayloadEmpty") shouldBe Some(Reason.PayloadEmpty)
  }

  it should "convert string \"PayloadTooLarge\" to Reason.PayloadTooLarge" in {
    parseReason("PayloadTooLarge") shouldBe Some(Reason.PayloadTooLarge)
  }

  it should "convert string \"BadTopic\" to Reason.BadTopic" in {
    parseReason("BadTopic") shouldBe Some(Reason.BadTopic)
  }

  it should "convert string \"TopicDisallowed\" to Reason.TopicDisallowed" in {
    parseReason("TopicDisallowed") shouldBe Some(Reason.TopicDisallowed)
  }

  it should "convert string \"BadMessageId\" to Reason.BadMessageId" in {
    parseReason("BadMessageId") shouldBe Some(Reason.BadMessageId)
  }

  it should "convert string \"BadExpirationDate\" to Reason.BadExpirationDate" in {
    parseReason("BadExpirationDate") shouldBe Some(Reason.BadExpirationDate)
  }

  it should "convert string \"BadPriority\" to Reason.BadPriority" in {
    parseReason("BadPriority") shouldBe Some(Reason.BadPriority)
  }

  it should "convert string \"MissingDeviceToken\" to Reason.MissingDeviceToken" in {
    parseReason("MissingDeviceToken") shouldBe Some(Reason.MissingDeviceToken)
  }

  it should "convert string \"BadDeviceToken\" to Reason.BadDeviceToken" in {
    parseReason("BadDeviceToken") shouldBe Some(Reason.BadDeviceToken)
  }

  it should "convert string \"DeviceTokenNotForTopic\" to Reason.DeviceTokenNotForTopic" in {
    parseReason("DeviceTokenNotForTopic") shouldBe Some(Reason.DeviceTokenNotForTopic)
  }

  it should "convert string \"Unregistered\" to Reason.Unregistered" in {
    parseReason("Unregistered") shouldBe Some(Reason.Unregistered)
  }

  it should "convert string \"DuplicateHeaders\" to Reason.DuplicateHeaders" in {
    parseReason("DuplicateHeaders") shouldBe Some(Reason.DuplicateHeaders)
  }

  it should "convert string \"BadCertificateEnvironment\" to Reason.BadCertificateEnvironment" in {
    parseReason("BadCertificateEnvironment") shouldBe Some(Reason.BadCertificateEnvironment)
  }

  it should "convert string \"BadCertificate\" to Reason.BadCertificate" in {
    parseReason("BadCertificate") shouldBe Some(Reason.BadCertificate)
  }

  it should "convert string \"Forbidden\" to Reason.Forbidden" in {
    parseReason("Forbidden") shouldBe Some(Reason.Forbidden)
  }

  it should "convert string \"BadPath\" to Reason.BadPath" in {
    parseReason("BadPath") shouldBe Some(Reason.BadPath)
  }

  it should "convert string \"MethodNotAllowed\" to Reason.MethodNotAllowed" in {
    parseReason("MethodNotAllowed") shouldBe Some(Reason.MethodNotAllowed)
  }

  it should "convert string \"TooManyRequests\" to Reason.TooManyRequests" in {
    parseReason("TooManyRequests") shouldBe Some(Reason.TooManyRequests)
  }

  it should "convert string \"IdleTimeout\" to Reason.IdleTimeout" in {
    parseReason("IdleTimeout") shouldBe Some(Reason.IdleTimeout)
  }

  it should "convert string \"Shutdown\" to Reason.Shutdown" in {
    parseReason("Shutdown") shouldBe Some(Reason.Shutdown)
  }

  it should "convert string \"InternalServerError\" to Reason.InternalServerError" in {
    parseReason("InternalServerError") shouldBe Some(Reason.InternalServerError)
  }

  it should "convert string \"ServiceUnavailable\" to Reason.ServiceUnavailable" in {
    parseReason("ServiceUnavailable") shouldBe Some(Reason.ServiceUnavailable)
  }

  it should "convert string \"MissingTopic\" to Reason.MissingTopic" in {
    parseReason("MissingTopic") shouldBe Some(Reason.MissingTopic)
  }

  it should "return None for other strings" in {
    parseReason("missingtopic") shouldBe None
  }

  "Method parseStatusCode" should "convert int 200 to StatusCode.OK" in {
    parseStatusCode(200) shouldBe Some(StatusCode.OK)
  }

  it should "convert int 400 to StatusCode.BadRequest" in {
    parseStatusCode(400) shouldBe Some(StatusCode.BadRequest)
  }

  it should "convert int 403 to StatusCode.Forbidden" in {
    parseStatusCode(403) shouldBe Some(StatusCode.Forbidden)
  }
  it should "convert int 405 to StatusCode.MethodNotAllowed" in {
    parseStatusCode(405) shouldBe Some(StatusCode.MethodNotAllowed)
  }

  it should "convert int 410 to StatusCode.Gone" in {
    parseStatusCode(410) shouldBe Some(StatusCode.Gone)
  }

  it should "convert int 413 to StatusCode.RequestEntityTooLarge" in {
    parseStatusCode(413) shouldBe Some(StatusCode.RequestEntityTooLarge)
  }

  it should "convert int 429 to StatusCode.TooManyRequests" in {
    parseStatusCode(429) shouldBe Some(StatusCode.TooManyRequests)
  }

  it should "convert int 500 to StatusCode.InternalServerError" in {
    parseStatusCode(500) shouldBe Some(StatusCode.InternalServerError)
  }

  it should "convert int 503 to StatusCode.ServiceUnavailable" in {
    parseStatusCode(503) shouldBe Some(StatusCode.ServiceUnavailable)
  }

  it should "return None for other integers" in {
    parseStatusCode(100) shouldBe None
  }
}
