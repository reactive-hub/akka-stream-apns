package reactivehub.akka.stream.apns

import reactivehub.akka.stream.apns.StatusCode.OK

/**
  * Response to a push notification request. It can be either Success or Failure.
  * Every response contains a status code - OK for Success, a failing status
  * code for Failure. In addition, failures also contain a reason and optionally
  * a timestamp. Timestamp is non-empty iff the status code is Gone.
  */
sealed abstract class Response {
  def isSuccess: Boolean
  def isFailure: Boolean
  def statusCode: StatusCode
}

object Response {
  case object Success extends Response {
    override val isSuccess = true
    override val isFailure = false
    override val statusCode = OK
  }

  final case class Failure(statusCode: StatusCode, reason: Reason,
      timestamp: Option[Long]) extends Response {
    require(statusCode.isFailure, "Failure cannot have a successful status code")
    require(
      (statusCode == StatusCode.Gone && timestamp.isDefined) ||
        (statusCode != StatusCode.Gone && timestamp.isEmpty),
      "Timestamp is non-empty iff the status code is Gone")
    override val isSuccess = false
    override val isFailure = true
  }
}

/**
  * Status code of the response to a push notification request.
  */
sealed abstract class StatusCode {
  def intValue: Int
  def defaultMessage: String
  def isSuccess: Boolean
  def isFailure: Boolean
}

object StatusCode {
  private[StatusCode] sealed class StatusCodeImpl(
      override val intValue: Int,
      override val defaultMessage: String,
      override val isSuccess: Boolean = false) extends StatusCode {
    override def isFailure: Boolean = !isSuccess
  }

  case object OK extends StatusCodeImpl(200, "Success", true)
  case object BadRequest extends StatusCodeImpl(400, "Bad request")
  case object Forbidden extends StatusCodeImpl(403, "There was an error with the certificate.")
  case object NotFound extends StatusCodeImpl(404, "Path not found.")
  case object MethodNotAllowed extends StatusCodeImpl(405, "The request used a bad :method value. Only POST requests are supported.")
  case object Gone extends StatusCodeImpl(410, "The device token is no longer active for the topic.")
  case object RequestEntityTooLarge extends StatusCodeImpl(413, "The notification payload was too large.")
  case object TooManyRequests extends StatusCodeImpl(429, "The server received too many requests for the same device token.")
  case object InternalServerError extends StatusCodeImpl(500, "Internal server error")
  case object ServiceUnavailable extends StatusCodeImpl(503, "The server is shutting down and unavailable.")
}

/**
  * Reason why the push notification failed or the connection was terminated
  * by the remote peer.
  */
sealed abstract class Reason

object Reason {
  case object PayloadEmpty extends Reason
  case object PayloadTooLarge extends Reason
  case object BadTopic extends Reason
  case object TopicDisallowed extends Reason
  case object BadMessageId extends Reason
  case object BadExpirationDate extends Reason
  case object BadPriority extends Reason
  case object MissingDeviceToken extends Reason
  case object BadDeviceToken extends Reason
  case object DeviceTokenNotForTopic extends Reason
  case object Unregistered extends Reason
  case object DuplicateHeaders extends Reason
  case object BadCertificateEnvironment extends Reason
  case object BadCertificate extends Reason
  case object Forbidden extends Reason
  case object BadPath extends Reason
  case object MethodNotAllowed extends Reason
  case object TooManyRequests extends Reason
  case object IdleTimeout extends Reason
  case object Shutdown extends Reason
  case object InternalServerError extends Reason
  case object ServiceUnavailable extends Reason
  case object MissingTopic extends Reason
}
