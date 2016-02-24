package reactivehub.akka.stream.apns

import akka.{Done, NotUsed}
import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import akka.stream.TLSProtocol._
import akka.stream.scaladsl._
import akka.stream.stage._
import akka.stream._
import akka.util.{ByteString, ByteStringBuilder}
import java.net.InetSocketAddress
import java.nio.ByteOrder
import javax.net.ssl.SSLContext
import reactivehub.akka.stream.apns.Environment.Production
import reactivehub.akka.stream.apns.ErrorStatus._
import scala.concurrent.{Future, Promise}
import scala.util.Try

class ApnsExt(implicit system: ExtendedActorSystem) extends Extension {
  def notificationService(sslContext: SSLContext): Flow[Notification, Error, NotUsed] =
    notificationService(Production, sslContext)

  def notificationService(environment: Environment, sslContext: SSLContext): Flow[Notification, Error, NotUsed] =
    notificationService(environment.gateway, sslContext)

  def notificationService(host: String, port: Int, sslContext: SSLContext): Flow[Notification, Error, NotUsed] =
    notificationService(new InetSocketAddress(host, port), sslContext)

  def notificationService(address: InetSocketAddress, sslContext: SSLContext): Flow[Notification, Error, NotUsed] =
    NotificationStage().atop(tlsStage(sslContext)).join(transportFlow(address))

  def feedbackService(sslContext: SSLContext): Source[Feedback, Promise[Done]] =
    feedbackService(Production, sslContext)

  def feedbackService(environment: Environment, sslContext: SSLContext): Source[Feedback, Promise[Done]] =
    feedbackService(environment.feedback, sslContext)

  def feedbackService(host: String, port: Int, sslContext: SSLContext): Source[Feedback, Promise[Done]] =
    feedbackService(new InetSocketAddress(host, port), sslContext)

  def feedbackService(address: InetSocketAddress, sslContext: SSLContext): Source[Feedback, Promise[Done]] =
    emptySource.via(FeedbackStage().atop(tlsStage(sslContext)).join(transportFlow(address)))

  private def tlsStage(sslContext: SSLContext) = TLS(sslContext, NegotiateNewSession.withDefaults, Client)

  private def transportFlow(address: InetSocketAddress) = Tcp().outgoingConnection(address)

  private def emptySource: Source[ByteString, Promise[Done]] =
    Source.maybe[ByteString].mapMaterializedValue { orig ⇒
      new Promise[Done] {
        import system.dispatcher
        override def future: Future[Done] = orig.future.map(_ ⇒ Done)
        override def isCompleted: Boolean = orig.isCompleted
        override def tryComplete(result: Try[Done]): Boolean = orig.tryComplete(result.map(_ ⇒ None))
      }
    }
}

object ApnsExt extends ExtensionKey[ApnsExt]

private[apns] object NotificationStage {
  implicit val _ = ByteOrder.BIG_ENDIAN

  def apply(): BidiFlow[Notification, SslTlsOutbound, SslTlsInbound, Error, NotUsed] = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
    val out = b.add(Flow[Notification].map(renderNotification).map(SendBytes))
    val in = b.add(Flow[SslTlsInbound]
      .collect({ case SessionBytes(_, bytes) ⇒ bytes })
      .via(new ErrorFraming)
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

  class ErrorFraming extends GraphStage[FlowShape[ByteString, ByteString]] {
    import ErrorFraming.frameLength

    val in = Inlet[ByteString]("ErrorFraming.in")
    val out = Outlet[ByteString]("ErrorFraming.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(attr: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      var stash = ByteString.empty

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          stash ++= grab(in)
          pushOrPull()
        }

        override def onUpstreamFinish(): Unit = {
          emitMultiple(out, stash.grouped(frameLength).filter(_.size == frameLength))
          complete(out)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pushOrPull()
      })

      private def pushOrPull(): Unit = {
        if (stash.size >= frameLength) {
          val frame = stash.take(frameLength)
          stash = stash.drop(frameLength)
          push(out, frame)
        } else pull(in)
      }
    }
  }

  object ErrorFraming {
    val frameLength = 6
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
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  def apply(): BidiFlow[ByteString, SslTlsOutbound, SslTlsInbound, Feedback, NotUsed] = BidiFlow.fromGraph(GraphDSL.create() { b ⇒
    val out = b.add(Flow[ByteString].map(SendBytes))
    val in = b.add(Flow[SslTlsInbound]
      .collect({ case SessionBytes(_, bytes) ⇒ bytes })
      .via(Framing.lengthField(fieldLength = 2, fieldOffset = 4, maximumFrameLength = 2 << 16 + 4 + 2, byteOrder))
      .map(parseFeedback))
    BidiShape.fromFlows(out, in)
  })

  def parseFeedback(response: ByteString): Feedback = {
    val bytes = response.iterator
    val timestamp = bytes.getLong
    val tokenLength = bytes.getShort
    Feedback(DeviceToken(bytes.take(tokenLength).toArray), timestamp)
  }
}
