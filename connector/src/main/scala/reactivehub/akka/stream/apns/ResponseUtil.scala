package reactivehub.akka.stream.apns

private[apns] object ResponseUtil {
  private val reasonMap = {
    import reactivehub.akka.stream.apns.Reason._

    Map(
      "PayloadEmpty" → PayloadEmpty,
      "PayloadTooLarge" → PayloadTooLarge,
      "BadTopic" → BadTopic,
      "TopicDisallowed" → TopicDisallowed,
      "BadMessageId" → BadMessageId,
      "BadExpirationDate" → BadExpirationDate,
      "BadPriority" → BadPriority,
      "MissingDeviceToken" → MissingDeviceToken,
      "BadDeviceToken" → BadDeviceToken,
      "DeviceTokenNotForTopic" → DeviceTokenNotForTopic,
      "Unregistered" → Unregistered,
      "DuplicateHeaders" → DuplicateHeaders,
      "BadCertificateEnvironment" → BadCertificateEnvironment,
      "BadCertificate" → BadCertificate,
      "Forbidden" → Forbidden,
      "BadPath" → BadPath,
      "MethodNotAllowed" → MethodNotAllowed,
      "TooManyRequests" → TooManyRequests,
      "IdleTimeout" → IdleTimeout,
      "Shutdown" → Shutdown,
      "InternalServerError" → InternalServerError,
      "ServiceUnavailable" → ServiceUnavailable,
      "MissingTopic" → MissingTopic)
  }

  /**
    * Returns a Reason wrapped in an Option for the string representation.
    * If the is no such Reason, None is returned.
    */
  def parseReason(str: String): Option[Reason] = reasonMap.get(str)

  private val statusCodeMap = {
    import reactivehub.akka.stream.apns.StatusCode._

    val statusCodes = Seq(OK, BadRequest, Forbidden, NotFound, MethodNotAllowed,
      Gone, RequestEntityTooLarge, TooManyRequests, InternalServerError,
      ServiceUnavailable)

    statusCodes.foldLeft(Map.empty[Int, StatusCode]) {
      case (m, sc) ⇒ m + (sc.intValue → sc)
    }
  }

  /**
    * Returns a StatusCode wrapped in an Option for the code. If the is no such
    * StatusCode, None is returned.
    */
  def parseStatusCode(code: Int): Option[StatusCode] = statusCodeMap.get(code)
}
