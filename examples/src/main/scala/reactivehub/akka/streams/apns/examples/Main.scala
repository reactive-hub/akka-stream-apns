package reactivehub.akka.streams.apns.examples

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import reactivehub.akka.stream.apns.Environment.Sandbox
import reactivehub.akka.stream.apns.TlsUtil.loadPkcs12FromResource
import reactivehub.akka.stream.apns._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App with SprayJsonSupport {
  implicit val system = ActorSystem("system")
  implicit val _ = ActorMaterializer()

  val apns = ApnsExt(system).notificationService(Sandbox, loadPkcs12FromResource("/cert.p12", "password"))

  val deviceToken = DeviceToken("C3F5B30029B78097568FB00588FBA9CA69D934BA56F2FB69BACD4F61DA722986")

  val payload = Payload.Builder()
    .withAlert("Hello!")
    .withBadge(1)

  val f = Source.single(Notification(deviceToken, payload)).via(apns).runForeach(println)

  Await.result(f, Duration.Inf)
  system.shutdown()
}
