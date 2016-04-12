package reactivehub.akka.stream.apns

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.{ImplicitSender, TestKit, TestKitBase, TestProbe}
import io.netty.buffer.{ByteBufAllocator, UnpooledByteBufAllocator}
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.{ChannelInputShutdownEvent, SocketChannel}
import io.netty.channel.{Channel, ChannelHandlerContext, _}
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.codec.string.{StringDecoder, StringEncoder}
import io.netty.util.CharsetUtil._
import java.net.{InetSocketAddress, SocketException}
import java.util.concurrent.TimeUnit
import org.scalatest._
import reactivehub.akka.stream.apns.helper.NettyHelpers
import reactivehub.akka.stream.apns.helper.NettyHelpers.{InputClosed, Received}
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

class NettyLogicSpec(_system: ActorSystem)
    extends TestKit(_system)
    with FlatSpecLike
    with EchoHelpers
    with ImplicitSender
    with Matchers
    with BeforeAndAfterAll {

  def this() = this(ActorSystem("NettyLogicSpec"))

  implicit val materializer = ActorMaterializer()

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  def nettyStage(f: ChannelHandler ⇒ ChannelFuture) =
    new TestableNettyStage[String, String] {
      override def createChannel(bridge: ChannelHandler): ChannelFuture =
        f(bridge)
    }

  "NettyLogic" should "should fail the stage when createChannel fails" in {
    val (pub, sub) = run(nettyStage(_ ⇒ throw new IllegalStateException))

    pub.expectCancellation()
    sub.expectSubscription()
    sub.expectErrorType[IllegalStateException]
  }

  it should "fail the stage when channel connect fails" in {
    val (pub, sub) = run(nettyStage { _ ⇒
      new EmbeddedChannel().newFailedFuture(new SocketException)
    })

    sub.expectSubscription()
    pub.expectCancellation()
    sub.expectErrorType[SocketException]
  }

  it should "complete the stage and close the channel when upstream finishes (before the bridge is installed)" in {
    val stage = nettyStage(_ ⇒ new EmbeddedChannel().newSucceededFuture())
    val (pub, sub) = run(stage)

    stage.expectConnected()

    pub.sendComplete()

    sub.expectSubscription()
    sub.expectComplete()
    stage.expectClosed()
  }

  it should "fail the stage and close the channel when upstream fails (before the bridge is installed)" in {
    val stage = nettyStage(_ ⇒ new EmbeddedChannel().newSucceededFuture())
    val (pub, sub) = run(stage)

    stage.expectConnected()

    pub.sendError(new RuntimeException)

    sub.expectSubscription()
    sub.expectErrorType[RuntimeException]
    stage.expectClosed()
  }

  it should "complete the stage and close the channel when downstream cancels before initialized" in {
    val stage = nettyStage(_ ⇒ new EmbeddedChannel().newSucceededFuture())
    val (pub, sub) = run(stage)

    stage.expectConnected()

    sub.cancel()

    pub.expectCancellation()
    stage.expectClosed()
  }

  it should "work with an echo server" in withServer { (stage, server) ⇒
    val (pub, sub) = run(stage)

    stage.expectConnected()
    server.expectConnection()

    sub.request(1)
    pub.sendNext("Hello\n")
    sub.expectNext("Hello")
  }

  it should "send and receive 10k messages" in
    withServer(autoClose = true) { (stage, server) ⇒
      val results = mutable.HashSet.empty[String]

      Await.result(Source.fromIterator(() ⇒ (1 to 10000).iterator)
        .map(i ⇒ s"Hello $i\n")
        .via(stage.async)
        .runWith(Sink.foreach(results += _)), 3.seconds)

      results.size shouldBe 10000
    }

  it should "be able to send data" in withServer { (stage, server) ⇒
    val (pub, _) = run(stage)

    stage.expectConnected()
    val connection = server.expectConnection()

    pub.sendNext("Hello\n")
    connection.expectData("Hello\n")
  }

  it should "be able receive data" in withServer { (stage, server) ⇒
    val (_, sub) = run(stage)

    stage.expectConnected()
    val connection = server.expectConnection()

    connection.write("Hello\n")
    sub.request(1)
    sub.expectNext("Hello")
  }

  it should "continue receiving when upstream completes" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      val connection = server.expectConnection()

      sub.request(2)
      pub.sendNext("Hello\n")
      sub.expectNext("Hello")

      pub.sendComplete()
      connection.expectInputClosed()

      connection.write("Hello2\n")
      sub.expectNext("Hello2")
    }

  it should "complete the stage when upstream completes and then server closes the write side" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      val connection = server.expectConnection()

      sub.request(1)

      pub.sendComplete()
      connection.expectInputClosed()

      connection.write("Hello\n")
      sub.expectNext("Hello")

      connection.shutdownOutput()
      sub.expectComplete()
      stage.expectClosed()
    }

  it should "complete the downstream and continue writing when server closes the write side" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      val connection = server.expectConnection()

      connection.shutdownOutput()
      sub.expectSubscriptionAndComplete()

      pub.sendNext("Hello\n")
      connection.expectData("Hello\n")
    }

  it should "complete the stage when server closes the write side and then upstream completes" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      val connection = server.expectConnection()

      connection.shutdownOutput()
      sub.expectSubscriptionAndComplete()

      pub.sendNext("Hello\n")
      connection.expectData("Hello\n")

      pub.sendComplete()
      stage.expectClosed()
    }

  it should "complete the stage and close the channel when downstream cancels and then upstream completes" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      sub.cancel()
      pub.sendComplete()
      stage.expectClosed()
    }

  it should "complete the stage and close the channel when upstream completes and then downstream cancels" in
    withServer { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()

      pub.sendComplete()
      sub.cancel()
      stage.expectClosed()
    }

  it should "fail the stage and close the channel when upstream fails" in
    withServer { (stage, server) ⇒
      val (pub, _) = run(stage)

      stage.expectConnected()

      pub.sendError(new RuntimeException)
      stage.expectClosed()
    }

  it should "complete the stage when server closes the connection" in
    withServer(halfClose = false) { (stage, server) ⇒
      val (pub, sub) = run(stage)

      stage.expectConnected()
      server.expectConnection()

      server.close()
      pub.expectCancellation()
      sub.expectSubscriptionAndComplete()
      stage.expectClosed()
    }
}

trait EchoHelpers extends NettyHelpers with BeforeAndAfterAll {
  this: TestKitBase with Suite ⇒

  final class EchoClientStage(
    remoteAddress: InetSocketAddress,
    group: NioEventLoopGroup,
    halfClose: Boolean = true,
    allocator: ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT)
      extends TestableSocketNettyStage[String, String](remoteAddress, group, halfClose, allocator) {

    override protected def setup(pipeline: ChannelPipeline, bridge: ChannelHandler): Unit = {
      pipeline.addLast(new LineBasedFrameDecoder(80))
      pipeline.addLast(new StringDecoder(UTF_8))
      pipeline.addLast(new StringEncoder(UTF_8))
      pipeline.addLast(bridge)
    }
  }

  final class EchoServerConnection(channel: SocketChannel, probe: TestProbe,
      dataProbe: TestProbe) {

    def expectInputClosed(): Unit = probe.expectMsg(InputClosed)
    def shutdownOutput(): Unit = channel.shutdownOutput()
    def expectData(data: String) = dataProbe.expectMsg(data)
    def write(data: String): Unit = channel.writeAndFlush(data)
  }

  final class EchoServer(
    group: NioEventLoopGroup,
    halfClose: Boolean = true,
    autoClose: Boolean = false,
    allocator: ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT)
      extends TestableServer[EchoServerConnection](group, halfClose, allocator = allocator) {

    override protected def setup(pipeline: ChannelPipeline): Unit = {
      pipeline.addLast(new LineBasedFrameDecoder(80, false, false))
      pipeline.addLast(new StringDecoder(UTF_8))
      pipeline.addLast(new StringEncoder(UTF_8))
      pipeline.addLast(new SimpleChannelInboundHandler[String] {
        override def channelRead0(ctx: ChannelHandlerContext, msg: String): Unit = {
          ctx.pipeline().fireUserEventTriggered(Received(msg))
          ctx.write(msg)
        }

        override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
          ctx.flush()

        override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
          ctx.channel().config().setAutoRead(ctx.channel().isWritable)
          ctx.fireChannelWritabilityChanged()
        }

        override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
          if (evt.isInstanceOf[ChannelInputShutdownEvent] && autoClose)
            ctx.channel().asInstanceOf[SocketChannel].shutdownOutput()
          ctx.fireUserEventTriggered(evt)
        }
      })
    }

    override protected def createConnection(channel: Channel, probe: TestProbe,
      dataProbe: TestProbe): EchoServerConnection =
      new EchoServerConnection(channel.asInstanceOf[SocketChannel], probe, dataProbe)
  }

  var group: NioEventLoopGroup = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    group = new NioEventLoopGroup()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
  }

  def withServer(f: (EchoClientStage, EchoServer) ⇒ Any): Unit =
    withServer()(f)

  def withServer(halfClose: Boolean = true, autoClose: Boolean = false)(f: (EchoClientStage, EchoServer) ⇒ Any): Unit = {
    val server = new EchoServer(group, autoClose = autoClose)
    try {
      val stage = new EchoClientStage(server.address, group, halfClose)
      f(stage, server)
    } finally {
      server.close()
    }
  }
}
