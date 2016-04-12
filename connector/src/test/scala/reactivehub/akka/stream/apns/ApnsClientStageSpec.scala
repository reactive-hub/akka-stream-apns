package reactivehub.akka.stream.apns

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{ImplicitSender, TestKit, TestKitBase, TestProbe}
import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.{Channel, _}
import io.netty.handler.codec.http2.{DefaultHttp2Headers, Http2Stream, _}
import io.netty.handler.ssl.ApplicationProtocolConfig._
import io.netty.handler.ssl._
import io.netty.handler.ssl.util.{InsecureTrustManagerFactory, SelfSignedCertificate}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.scalatest._
import reactivehub.akka.stream.apns.ApnsConnectionHandler._
import reactivehub.akka.stream.apns.ApnsHelpers.{Close, ReceivedRequest}
import reactivehub.akka.stream.apns.helper.NettyHelpers
import reactivehub.akka.stream.apns.helper.NettyHelpers._
import reactivehub.akka.stream.apns.marshallers.CirceSupport
import reactivehub.akka.stream.apns.{Reason ⇒ R, StatusCode ⇒ Sc}
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Try}

class ApnsClientStageSpec(_system: ActorSystem)
    extends TestKit(_system)
    with FlatSpecLike
    with ApnsHelpers
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll
    with CirceSupport {

  def this() = this(ActorSystem("ApnsClientStage"))

  implicit val materializer = ActorMaterializer()

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  val validPayload = Payload.Builder().withAlert("Hello").withBadge(1).result

  "ApnsClientStage" should "accept a valid notification" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      pub.sendNext(1 → Notification(ApnsHelpers.knownToken, validPayload))
      pub.sendComplete()

      sub.requestNext(1 → Response.Success)
      sub.expectComplete()
    }

  it should "accept a valid notification with known topic" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      pub.sendNext(1 → Notification(ApnsHelpers.knownToken, validPayload,
        topic = Some(ApnsHelpers.knownTopic)))
      pub.sendComplete()

      sub.requestNext(1 → Response.Success)
      sub.expectComplete()
    }

  it should "reject a notification with unknown token" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      pub.sendNext(1 → Notification(ApnsHelpers.unknownToken, validPayload))
      pub.sendComplete()

      sub.requestNext(1 → Response.Failure(Sc.BadRequest, R.BadDeviceToken, None))
      sub.expectComplete()
    }

  it should "reject a notification with unknown topic" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      pub.sendNext(1 → Notification(ApnsHelpers.knownToken, validPayload,
        topic = Some(ApnsHelpers.unknownTopic)))
      pub.sendComplete()

      sub.requestNext(1 → Response.Failure(Sc.BadRequest, R.TopicDisallowed, None))
      sub.expectComplete()
    }

  it should "be able to process 10k notifications" in
    withServer { (stage, server) ⇒
      val results = mutable.HashSet.empty[Int]

      Await.result(Source.fromIterator(() ⇒ (1 to 10000).iterator)
        .map(i ⇒ i → Notification(ApnsHelpers.knownToken, validPayload))
        .via(stage)
        .runWith(Sink.foreach(results += _._1)), 10.seconds)

      results.size shouldBe 10000
    }

  it should "handle when connection is closed by the server" in
    withServer { (stage, server) ⇒
      val (_, sub, m) = runMat(stage)

      val connection = server.expectConnection()
      connection.close(Some(R.Shutdown))

      sub.expectSubscriptionAndComplete()

      Await.result(m, 10.seconds) shouldBe Some(R.Shutdown)
    }

  it should "handle when connection is closed by the server with no reason" in
    withServer { (stage, server) ⇒
      val (_, sub, m) = runMat(stage)

      val connection = server.expectConnection()
      connection.close(None)

      sub.expectSubscriptionAndComplete()

      Await.result(m, 10.seconds) shouldBe None
    }
}

private[apns] object ApnsHelpers {
  final case class ReceivedRequest(
    method: Option[String],
    path: Option[String],
    id: Option[String],
    expiration: Option[String],
    priority: Option[String],
    topic: Option[String],
    payload: Option[String])

  final case class Close(reason: Option[Reason])

  val knownToken =
    DeviceToken("a7729a6af17775f80fdd8042b20e2a6f9fb1df2bc10f6bdb896ca41a30ba0455")

  val unregisteredToken =
    DeviceToken("a7729a6af17775f80fdd8042b20e2a6f9fb1df2bc10f6bdb896ca41a30ba0456")

  val unknownToken =
    DeviceToken("a7729a6af17775f80fdd8042b20e2a6f9fb1df2bc10f6bdb896ca41a30ba0457")

  val knownTopic = "topic"
  val unknownTopic = "non-topic"
}

private[apns] trait ApnsHelpers extends NettyHelpers with BeforeAndAfterAll {
  this: TestKitBase with Suite ⇒

  final class ApnsServerConnection(channel: SocketChannel, probe: TestProbe,
      dataProbe: TestProbe) {

    def expectData(pf: PartialFunction[Any, ReceivedRequest]) =
      dataProbe.expectMsgPF[ReceivedRequest]()(pf)

    def close(reason: Option[Reason]) =
      channel.pipeline().fireUserEventTriggered(Close(reason))
  }

  final class ApnsServerMock(sslContext: SslContext, group: NioEventLoopGroup)
      extends TestableServer[ApnsServerConnection](group, halfClose = false,
        channelReadyWhenActive = false) {

    override protected def setup(pipeline: ChannelPipeline): Unit = {
      pipeline.addLast(sslContext.newHandler(channel.alloc()))
      pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
        override def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit =
          if (ApplicationProtocolNames.HTTP_2.equals(protocol))
            pipeline.addLast(new Builder().build())
          else throw new IllegalStateException(s"Unexpected protocol $protocol")
      })
    }

    override protected def createConnection(channel: Channel, probe: TestProbe,
      dataProbe: TestProbe): ApnsServerConnection =
      new ApnsServerConnection(channel.asInstanceOf[SocketChannel], probe, dataProbe)
  }

  private class Builder extends AbstractHttp2ConnectionHandlerBuilder[ApnsServerHandler, Builder] {
    override def build(): ApnsServerHandler = super.build()

    override def build(
      decoder: Http2ConnectionDecoder,
      encoder: Http2ConnectionEncoder,
      initialSettings: Http2Settings): ApnsServerHandler = {

      val handler = new ApnsServerHandler(decoder, encoder, initialSettings)
      frameListener(new handler.Listener)
      handler
    }
  }

  private class ApnsServerHandler(
    decoder: Http2ConnectionDecoder,
    encoder: Http2ConnectionEncoder,
    initialSettings: Http2Settings)
      extends Http2ConnectionHandler(decoder, encoder, initialSettings) {

    private val conn = connection()
    private val dataKey = conn.newKey()
    private val headersKey = conn.newKey()

    override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
      if (ctx.channel().isActive)
        ctx.pipeline().fireUserEventTriggered(ChannelReady)
      super.handlerAdded(ctx)
    }

    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      ctx.pipeline().fireUserEventTriggered(ChannelReady)
      super.channelActive(ctx)
    }

    override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
      evt match {
        case Close(reason) ⇒ sendGoAway(ctx, reason)
        case _             ⇒
      }
      super.userEventTriggered(ctx, evt)
    }

    class Listener extends Http2EventAdapter {
      override def onDataRead(ctx: ChannelHandlerContext, streamId: Int,
        data: ByteBuf, padding: Int, endOfStream: Boolean): Int = {

        val stream = connection.stream(streamId)
        val builder = stream.getProperty[StringBuilder](dataKey) match {
          case null ⇒ StringBuilder.newBuilder.append(data.toString(UTF_8))
          case b    ⇒ b.append(data.toString(UTF_8))
        }

        if (endOfStream) {
          val headers = stream.getProperty[Http2Headers](headersKey)
          fireChannelRead(ctx, stream, headers, Some(builder.toString()))
        } else stream.setProperty(dataKey, builder)

        data.readableBytes + padding
      }

      override def onHeadersRead(ctx: ChannelHandlerContext, streamId: Int,
        headers: Http2Headers, streamDependency: Int, weight: Short,
        exclusive: Boolean, padding: Int, endStream: Boolean): Unit =
        onHeadersRead(ctx, streamId, headers, padding, endStream)

      override def onHeadersRead(ctx: ChannelHandlerContext, streamId: Int,
        headers: Http2Headers, padding: Int, endStream: Boolean): Unit = {

        val stream = connection.stream(streamId)
        val newHeaders = stream.getProperty[Http2Headers](headersKey) match {
          case null ⇒ new DefaultHttp2Headers().add(headers)
          case prev ⇒ prev.add(headers)
        }

        if (endStream) {
          val builder = Option(stream.getProperty[StringBuilder](dataKey))
          fireChannelRead(ctx, stream, newHeaders, builder.map(_.toString))
        } else stream.setProperty(headersKey, newHeaders)
      }

      override def onStreamRemoved(stream: Http2Stream): Unit =
        removeStreamData(stream)

      private def removeStreamData(stream: Http2Stream): Unit = {
        stream.removeProperty[Http2Headers](headersKey)
        stream.removeProperty[StringBuilder](dataKey)
      }

      private val Path = """\A/3/device/([0-9A-Za-z]+)\z""".r

      private def fireChannelRead(ctx: ChannelHandlerContext, stream: Http2Stream,
        headers: Http2Headers, payload: Option[String]): Unit = {

        removeStreamData(stream)

        val method = Option(headers.method()).map(_.toString)
        val path = Option(headers.path()).map(_.toString)
        val id = Option(headers.get(HeaderId)).map(_.toString)
        val expiration = Option(headers.get(HeaderExpiration)).map(_.toString)
        val priority = Option(headers.get(HeaderPriority)).map(_.toString)
        val topic = Option(headers.get(HeaderTopic)).map(_.toString)

        ctx.pipeline().fireUserEventTriggered(Received(
          new ReceivedRequest(method, path, id, expiration, priority, topic, payload)))

        if (!method.contains("POST"))
          sendNok(ctx, stream, id, Sc.MethodNotAllowed, R.MethodNotAllowed)
        else path match {
          case Some(Path(t)) ⇒
            if (!validateId(id))
              sendNok(ctx, stream, id, Sc.BadRequest, R.BadMessageId)
            else if (!validateDeviceToken(t))
              sendNok(ctx, stream, id, Sc.BadRequest, R.BadDeviceToken)
            else if (!validateExpiration(expiration))
              sendNok(ctx, stream, fixId(id), Sc.BadRequest, R.BadExpirationDate)
            else if (!validatePriority(priority))
              sendNok(ctx, stream, fixId(id), Sc.BadRequest, R.BadPriority)
            else if (!topic.forall(_ == ApnsHelpers.knownTopic))
              sendNok(ctx, stream, fixId(id), Sc.BadRequest, R.TopicDisallowed)
            else
              sendOk(ctx, stream, fixId(id))

          case _ ⇒
            sendNok(ctx, stream, id, Sc.NotFound, R.BadPath)
        }
      }
    }

    private def validateId(id: Option[String]): Boolean =
      id match {
        case Some(s) ⇒ Try(UUID.fromString(s)).isSuccess
        case None    ⇒ true
      }

    private def validateExpiration(expiration: Option[String]): Boolean =
      expiration match {
        case Some(s) ⇒ Try(Integer.parseInt(s)).isSuccess
        case None    ⇒ true
      }

    private def validatePriority(priority: Option[String]): Boolean =
      priority match {
        case Some("5") | Some("10") ⇒ true
        case None                   ⇒ true
        case _                      ⇒ false
      }

    private def validateDeviceToken(deviceToken: String): Boolean =
      Try(DeviceToken(deviceToken)) match {
        case Success(ApnsHelpers.knownToken)        ⇒ true
        case Success(ApnsHelpers.unregisteredToken) ⇒ true
        case _                                      ⇒ false
      }

    private def sendOk(ctx: ChannelHandlerContext, stream: Http2Stream,
      id: Option[String]): Unit = {

      val headers = new DefaultHttp2Headers().status(Sc.OK.intValue.toString)
      id.foreach(headers.set(HeaderId, _))

      encoder.writeHeaders(ctx, stream.id(), headers, 0, true, ctx.newPromise())
      ctx.flush()
    }

    private def sendNok(ctx: ChannelHandlerContext, stream: Http2Stream,
      id: Option[String], statusCode: StatusCode, reason: Reason,
      timestamp: Option[Long] = None): Unit = {

      val headers = new DefaultHttp2Headers().status(statusCode.intValue.toString)
      id.foreach(headers.set(HeaderId, _))

      encoder.writeHeaders(ctx, stream.id(), headers, 0, false, ctx.newPromise())
      encoder.writeData(ctx, stream.id(),
        ByteBufUtil.writeUtf8(ctx.alloc(), writeReason(reason, timestamp)),
        0, true, ctx.newPromise())
      ctx.flush()
    }

    private def sendGoAway(ctx: ChannelHandlerContext, reason: Option[Reason]): Unit = {
      goAway(ctx, connection.remote.lastStreamCreated(), Http2Error.CANCEL.code(),
        reason.map(r ⇒ ByteBufUtil.writeUtf8(ctx.alloc(), writeReason(r)))
          .getOrElse(Unpooled.EMPTY_BUFFER), ctx.newPromise())
      ctx.flush()
    }

    private def writeReason(reason: Reason, timestamp: Option[Long] = None): String = {
      val r = reason.toString
      timestamp match {
        case Some(t) ⇒ s"""{"reason":"$r", "timestamp":"$t"}"""
        case None    ⇒ s"""{"reason":"$r"}"""
      }
    }

    private def fixId(id: Option[String]): Option[String] =
      Try(id.map(UUID.fromString))
        .recover({ case _ ⇒ None }).get
        .orElse(Some(UUID.randomUUID()))
        .map(_.toString)
  }

  var serverGroup: NioEventLoopGroup = _
  var clientGroup: NioEventLoopGroup = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    serverGroup = new NioEventLoopGroup()
    clientGroup = new NioEventLoopGroup()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
    serverGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
  }

  def withServer(f: (ApnsClientStage[Int, NioSocketChannel], ApnsServerMock) ⇒ Any)(implicit ru: ResponseUnmarshaller): Unit = {
    val server = new ApnsServerMock(serverSslContext, serverGroup)
    try {
      val stage = NioApnsClientStage[Int](server.address, clientSslContext, clientGroup)
      f(stage, server)
    } finally {
      server.close()
    }
  }

  def serverSslContext: SslContext = {
    val ssc = new SelfSignedCertificate()
    SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(SslProvider.JDK)
      .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
      .applicationProtocolConfig(new ApplicationProtocolConfig(
        Protocol.ALPN,
        SelectorFailureBehavior.NO_ADVERTISE,
        SelectedListenerFailureBehavior.ACCEPT,
        ApplicationProtocolNames.HTTP_2))
      .build()
  }

  def clientSslContext: SslContext =
    SslContextBuilder.forClient()
      .sslProvider(SslProvider.JDK)
      .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .applicationProtocolConfig(new ApplicationProtocolConfig(
        Protocol.ALPN,
        SelectorFailureBehavior.NO_ADVERTISE,
        SelectedListenerFailureBehavior.ACCEPT,
        ApplicationProtocolNames.HTTP_2))
      .build()
}
