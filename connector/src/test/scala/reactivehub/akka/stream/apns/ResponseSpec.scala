package reactivehub.akka.stream.apns

import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.Response.{Failure, Success}

class ResponseSpec extends FlatSpec with Matchers {
  "Success" should "be successful" in {
    Success.isSuccess shouldBe true
    Success.isFailure shouldBe false
  }

  it should "have status code OK" in {
    Success.statusCode shouldBe StatusCode.OK
  }

  "Failure" should "be failing" in {
    val failure = Failure(StatusCode.Forbidden, Reason.BadCertificate, None)
    failure.isSuccess shouldBe false
    failure.isFailure shouldBe true
  }

  it should "fail for status code OK" in {
    intercept[IllegalArgumentException] {
      Failure(StatusCode.OK, Reason.BadCertificate, None)
    }
  }

  it should "support status code Gone and a non-empty timestamp" in {
    Failure(StatusCode.Gone, Reason.Unregistered, Some(12345L))
  }

  it should "fail if status code is Gone and timestamp is None" in {
    intercept[IllegalArgumentException] {
      Failure(StatusCode.Gone, Reason.Unregistered, None)
    }
  }

  it should "fail if status code is not Gone and timestamp is non-empty" in {
    intercept[IllegalArgumentException] {
      Failure(StatusCode.BadRequest, Reason.Unregistered, Some(12345L))
    }
  }
}
