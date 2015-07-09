# Reactive APNs Connector

Akka-stream-apns is an [Apple Push Notification Service](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html)
(APNs) connector built on top of [Akka Streams](http://akka.io).

## Quick Start

To use the connector, you need a [push notification client SSL certificate](https://developer.apple.com/library/ios/documentation/IDEs/Conceptual/AppDistributionGuide/ConfiguringPushNotifications/ConfiguringPushNotifications.html)
and a [device token](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/IPhoneOSClientImp.html).

```scala
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

  // Flow[Notification, Error, Unit]
  val apns = ApnsExt(system).notificationService(Sandbox, loadPkcs12FromResource("/cert.p12", "password"))

  val deviceToken = DeviceToken("64-chars hex string")

  val payload = Payload.Builder()
    .withAlert("Hello!")
    .withBadge(1)

  val f = Source.single(Notification(deviceToken, payload)).via(apns).runForeach(println)

  Await.result(f, Duration.Inf)
  system.shutdown()
}
```

## Payload Builder

Akka-stream-apns comes with a convenient payload builder. The payload is a JSON object with the required key `apns` and
zero or more custom keys. The builder can use a JSON library of your choice; just mix in
`SprayJsonSupport`, `PlayJsonSupport` or `LiftJsonSupport` or bring into scope your own `PayloadMarshaller`.

```scala
val payload = Payload.Builder()
  .withAlert("Bob wants to play poker")
  .withLocalizedAlert("GAME_PLAY_REQUEST_FORMAT", "Jenna", "Frank")
  .withTitle("Game Request")
  .withLocalizedTitle("GAME_PLAY_REQUEST_TITLE")
  .withLocalizedAction("PLAY")
  .withLaunchImage("Default.png")
  .withBadge(9)
  .withSound("chime.aiff")
  .withContentAvailable
  .withCustomField("acme1", "bar")
  .withCustomField("acme2", 42)
```

## Limitations

* No reconnect/resend on error (yet)
