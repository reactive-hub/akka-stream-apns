package reactivehub.akka.stream.apns

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import akka.stream.BidiShape
import akka.stream.io._
import akka.stream.scaladsl._
import akka.stream.stage.{Context, PushPullStage, SyncDirective, TerminationDirective}
import akka.util.{ByteString, ByteStringBuilder}
import java.net.InetSocketAddress
import java.nio.ByteOrder
import javax.net.ssl.SSLContext
import reactivehub.akka.stream.apns.Environment.Production
import reactivehub.akka.stream.apns.ErrorStatus._
import scala.concurrent.Promise

class ApnsExt(implicit system: ExtendedActorSystem) extends Extension {
  def notificationService(sslContext: SSLContext): Flow[Notification, Error, Unit] =
    notificationService(Production, sslContext)

  def notificationService(environment: Environment, sslContext: SSLContext): Flow[Notification, Error, Unit] =
    notificationService(environment.gateway, sslContext)

  def notificationService(host: String, port: Int, sslContext: SSLContext): Flow[Notification, Error, Unit] =
    notificationService(new InetSocketAddress(host, port), sslContext)

  def notificationService(address: InetSocketAddress, sslContext: SSLContext): Flow[Notification, Error, Unit] =
    NotificationStage().atop(tlsStage(sslContext)).join(transportFlow(address))

  def feedbackService(sslContext: SSLContext): Source[Feedback, Promise[Option[ByteString]]] =
    feedbackService(Production, sslContext)

  def feedbackService(environment: Environment, sslContext: SSLContext): Source[Feedback, Promise[Option[ByteString]]] =
    feedbackService(environment.feedback, sslContext)

  def feedbackService(host: String, port: Int, sslContext: SSLContext): Source[Feedback, Promise[Option[ByteString]]] =
    feedbackService(new InetSocketAddress(host, port), sslContext)

  def feedbackService(address: InetSocketAddress, sslContext: SSLContext): Source[Feedback, Promise[Option[ByteString]]] =
    Source.maybe[ByteString].via(FeedbackStage().atop(tlsStage(sslContext)).join(transportFlow(address)))

  private def tlsStage(sslContext: SSLContext) = SslTls(sslContext, NegotiateNewSession.withDefaults, Client)

  private def transportFlow(address: InetSocketAddress) = Tcp().outgoingConnection(address)
}

object ApnsExt extends ExtensionKey[ApnsExt]

private[apns] object NotificationStage {
  implicit val _ = ByteOrder.BIG_ENDIAN

  def apply(): BidiFlow[Notification, SslTlsOutbound, SslTlsInbound, Error, Unit] = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
    val out = b.add(Flow[Notification].map(renderNotification).map(SendBytes))
    val in = b.add(Flow[SslTlsInbound]
      .collect({ case SessionBytes(_, bytes) ⇒ bytes })
      .transform(() ⇒ new ErrorFraming)
      .map(parseError))
    BidiShape.fromFlows(out, in)
  })

  def renderNotification(notification: Notification): ByteString = {
    def renderDeviceToken(builder: ByteStringBuilder, deviceToken: DeviceToken): Unit = {
      builder.putByte(1)
      builder.putShort(deviceToken.value.length)
      builder.putBytes(deviceToken.value)
    }

    def renderPayload(builder: ByteStringBuilder, payload: Payload): Unit = {
      builder.putByte(2)
      builder.putShort(payload.value.length)
      builder.append(payload.value)
    }

    def renderIdentifier(builder: ByteStringBuilder, identifier: Int): Unit = {
      builder.putByte(3)
      builder.putShort(4)
      builder.putInt(identifier)
    }

    def renderExpirationDate(builder: ByteStringBuilder, expirationDate: Expiration): Unit = {
      builder.putByte(4)
      builder.putShort(4)
      builder.putInt(expirationDate.value)
    }

    def renderPriority(builder: ByteStringBuilder, priority: Priority): Unit = {
      builder.putByte(5)
      builder.putShort(1)
      priority match {
        case Priority.High ⇒ builder.putByte(10)
        case Priority.Low  ⇒ builder.putByte(5)
      }
    }

    import notification._

    val data = ByteString.newBuilder
    renderDeviceToken(data, deviceToken)
    renderPayload(data, payload)
    identifier.foreach(renderIdentifier(data, _))
    expiration.foreach(renderExpirationDate(data, _))
    priority.foreach(renderPriority(data, _))

    val frame = ByteString.newBuilder
    frame.putByte(2)
    frame.putInt(data.length)
    frame.append(data.result())
    frame.result()
  }

  class ErrorFraming extends PushPullStage[ByteString, ByteString] {
    var stash = ByteString.empty

    override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
      stash ++= elem
      pushOrPull(ctx)
    }

    override def onPull(ctx: Context[ByteString]): SyncDirective = pushOrPull(ctx)

    override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective =
      if (stash.isEmpty) ctx.finish() else ctx.absorbTermination()

    private def pushOrPull(ctx: Context[ByteString]): SyncDirective =
      if (stash.size >= 6) {
        val frame = stash.take(6)
        stash = stash.drop(6)
        ctx.push(frame)
      } else pullOrFinish(ctx)

    private def pullOrFinish(ctx: Context[ByteString]) = if (ctx.isFinishing) ctx.finish() else ctx.pull()
  }

  def parseError(response: ByteString): Error = {
    val bytes = response.iterator
    if (bytes.getByte != 8) throw new RuntimeException("Error response must have command byte set to 8")

    val status = bytes.getByte match {
      case 0   ⇒ NoError
      case 1   ⇒ ProcessingError
      case 2   ⇒ MissingDeviceToken
      case 3   ⇒ MissingTopic
      case 4   ⇒ MissingPayload
      case 5   ⇒ InvalidTokenSize
      case 6   ⇒ InvalidTopicSize
      case 7   ⇒ InvalidPayloadSize
      case 8   ⇒ InvalidToken
      case 10  ⇒ Shutdown
      case 255 ⇒ Unknown
      case s   ⇒ throw new RuntimeException(s"Invalid error status $s")
    }

    Error(status, bytes.getInt)
  }
}

private[apns] object FeedbackStage {
  implicit val _ = ByteOrder.BIG_ENDIAN

  def apply(): BidiFlow[ByteString, SslTlsOutbound, SslTlsInbound, Feedback, Unit] = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
    val out = b.add(Flow[ByteString].map(SendBytes))
    val in = b.add(Flow[SslTlsInbound]
      .collect({ case SessionBytes(_, bytes) ⇒ bytes })
      .transform(() ⇒ new FeedbackFraming)
      .map(parseFeedback))
    BidiShape.fromFlows(out, in)
  })

  class FeedbackFraming extends PushPullStage[ByteString, ByteString] {
    var stash = ByteString.empty
    var need = -1

    override def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = {
      stash ++= elem
      pushOrPull(ctx)
    }

    override def onPull(ctx: Context[ByteString]): SyncDirective = pushOrPull(ctx)

    override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective =
      if (stash.isEmpty) ctx.finish() else ctx.absorbTermination()

    private def pushOrPull(ctx: Context[ByteString]): SyncDirective =
      if (need == -1) {
        if (stash.size >= 6) {
          val tokenLength = stash.drop(4).iterator.getShort
          need = 6 + tokenLength
          pushOrPull(ctx)
        } else pullOrFinish(ctx)
      } else if (stash.size >= need) {
        val frame = stash.take(need)
        stash = stash.drop(need)
        need = -1
        ctx.push(frame)
      } else pullOrFinish(ctx)

    private def pullOrFinish(ctx: Context[ByteString]) = if (ctx.isFinishing) ctx.finish() else ctx.pull()
  }

  def parseFeedback(response: ByteString): Feedback = {
    val bytes = response.iterator
    val timestamp = bytes.getLong
    val tokenLength = bytes.getShort
    Feedback(DeviceToken(bytes.take(tokenLength).toArray), timestamp)
  }
}
