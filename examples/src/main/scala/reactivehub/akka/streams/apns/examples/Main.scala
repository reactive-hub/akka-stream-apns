package reactivehub.akka.streams.apns.examples

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import io.netty.channel.nio.NioEventLoopGroup
import reactivehub.akka.stream.apns.Environment.Development
import reactivehub.akka.stream.apns.TlsUtil.loadPkcs12FromResource
import reactivehub.akka.stream.apns._
import reactivehub.akka.stream.apns.marshallers.SprayJsonSupport

object Main extends App with SprayJsonSupport {
  implicit val system = ActorSystem("system")
  implicit val _ = ActorMaterializer()

  import system.dispatcher

  val group = new NioEventLoopGroup()
  val apns = ApnsExt(system).connection[Int](
    Development,
    loadPkcs12FromResource("/cert.p12", "password"),
    group)

  val deviceToken = DeviceToken("a7729a6af17775f80fdd8042b20e2a6f9fb1df2bc10f6bdb896ca41a30ba0455")

  val payload = Payload.Builder()
    .withAlert("Hello!")
    .withBadge(1)

  val source = Source.single(1 → Notification(deviceToken, payload))

  source.via(apns).runForeach(println)
    .onComplete { _ ⇒
      group.shutdownGracefully()
      system.terminate()
    }
}
