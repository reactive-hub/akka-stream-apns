package reactivehub.akka.stream.apns

/**
  * Reads response details from a JSON string returned by APNs servers for
  * failed requests or remotely terminated connections.
  */
trait ResponseUnmarshaller {
  def read(str: String): ResponseBody
}

final case class ResponseBody(reason: Reason, timestamp: Option[Long])
