package reactivehub.akka.stream.apns

final case class Error(status: ErrorStatus, identifier: Long)

sealed trait ErrorStatus

object ErrorStatus {
  case object NoError extends ErrorStatus
  case object ProcessingError extends ErrorStatus
  case object MissingDeviceToken extends ErrorStatus
  case object MissingTopic extends ErrorStatus
  case object MissingPayload extends ErrorStatus
  case object InvalidTokenSize extends ErrorStatus
  case object InvalidTopicSize extends ErrorStatus
  case object InvalidPayloadSize extends ErrorStatus
  case object InvalidToken extends ErrorStatus
  case object Shutdown extends ErrorStatus
  case object Unknown extends ErrorStatus
}
