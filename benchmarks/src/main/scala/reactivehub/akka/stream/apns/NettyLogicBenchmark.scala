package reactivehub.akka.stream.apns

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source, Tcp}
import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, _}
import akka.util.ByteString
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.socket.{ChannelInputShutdownEvent, SocketChannel}
import io.netty.handler.codec._
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import reactivehub.akka.stream.apns.NettyLogicBenchmark._
import reactivehub.akka.stream.apns.helper._
import scala.concurrent.Await
import scala.concurrent.duration._

object NettyLogicBenchmark {
  val data = ByteString("Test data")
  val operations = 100000

  def source: Source[ByteString, NotUsed] =
    Source.fromIterator(() ⇒ (1 to operations).iterator).map(_ ⇒ data)

  def clientChannel(remoteAddress: InetSocketAddress, group: NioEventLoopGroup,
    handlers: ChannelHandler*): ChannelFuture =
    new Bootstrap()
      .group(group)
      .channel(classOf[NioSocketChannel])
      .option(ChannelOption.ALLOW_HALF_CLOSURE, Boolean.box(true))
      .handler(new ChannelInitializer[NioSocketChannel] {
        override def initChannel(ch: NioSocketChannel): Unit = {
          val pipeline = ch.pipeline()
          pipeline.addLast(new FixedLengthFrameDecoder(data.length))
          pipeline.addLast(new ByteStringDecoder)
          pipeline.addLast(new ByteStringEncoder)
          pipeline.addLast(handlers: _*)
        }
      })
      .connect(remoteAddress)

  class NettyClient(
    remoteAddress: InetSocketAddress,
    group: NioEventLoopGroup,
    frameLength: Int)
      extends GraphStage[FlowShape[ByteString, ByteString]] {

    val in = Inlet[ByteString]("in")
    val out = Outlet[ByteString]("out")

    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new NettyLogic(shape) {
        override protected def createChannel(bridge: ChannelHandler): ChannelFuture =
          clientChannel(remoteAddress, group, bridge)
      }
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@OperationsPerInvocation(100000)
@State(Scope.Benchmark)
class NettyLogicBenchmark {
  var group: NioEventLoopGroup = _
  var system: ActorSystem = _
  var materializer: ActorMaterializer = _
  var server: EchoServer = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    group = new NioEventLoopGroup()
    system = ActorSystem("EchoBenchmark")
    materializer = ActorMaterializer()(system)
    server = new EchoServer(group)
  }

  @TearDown(Level.Iteration)
  def stopActorSystem(): Unit = {
    Await.result(system.terminate(), 30.seconds)
    server.closeSync()
    group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
  }

  @Benchmark
  def testAkkaStreamTcp(blackHole: Blackhole): Unit = {
    val f = source
      .via(Tcp(system).outgoingConnection(server.address))
      .via(new FixedLengthFraming(data.length))
      .runWith(Sink.foreach(blackHole.consume))(materializer)
    Await.result(f, 30.seconds)
  }

  @Benchmark
  def testNettyBridge(blackHole: Blackhole): Unit = {
    val f = source
      .via(new NettyClient(server.address, group, data.length))
      .runWith(Sink.foreach(blackHole.consume))(materializer)
    Await.result(f, 30.seconds)
  }

  @Benchmark
  def testNettyClient(blackHole: Blackhole): Unit = {
    val channel = clientChannel(server.address, group,
      new ChannelInboundHandlerAdapter {
        override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit =
          if (evt.isInstanceOf[ChannelInputShutdownEvent]) ctx.close()
      },

      new SimpleChannelInboundHandler[ByteString] {
        override def channelRead0(ctx: ChannelHandlerContext, msg: ByteString): Unit =
          blackHole.consume(msg)
      }).sync().channel().asInstanceOf[SocketChannel]

    var f: ChannelFuture = null
    for (i ← 1 to operations) {
      f = channel.write(data)
      if (!channel.isWritable) {
        channel.flush()
        f.sync()
      } else if (i % 16 == 0) channel.flush()
    }
    channel.flush()

    f.addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit =
        channel.shutdownOutput()
    })

    channel.closeFuture().await(30, TimeUnit.SECONDS)
  }
}
