# Reactive APNs Connector

Akka-stream-apns is an [Apple Push Notification Service](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html)
(APNs) connector built on top of [Akka Streams](http://akka.io). As of version 0.2,
akka-stream-apns uses the latest HTTP/2-based APNs provider API.

## Installation

```scala
resolvers += Resolver.bintrayRepo("reactivehub", "maven")

libraryDependencies += "com.reactivehub" %% "akka-stream-apns" % "0.2"
```

## Quick Start

To use the connector, you need a [push notification client SSL certificate](https://developer.apple.com/library/ios/documentation/IDEs/Conceptual/AppDistributionGuide/ConfiguringPushNotifications/ConfiguringPushNotifications.html)
and a [device token](https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/IPhoneOSClientImp.html).
See System Requirements for more details on how to choose a TLS provider.

```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import io.netty.channel.nio.NioEventLoopGroup
import reactivehub.akka.stream.apns.TlsUtil.loadPkcs12FromResource
import reactivehub.akka.stream.apns._
import reactivehub.akka.stream.apns.marshallers.SprayJsonSupport

object Main extends App with SprayJsonSupport {
  implicit val system = ActorSystem("system")
  implicit val _ = ActorMaterializer()

  import system.dispatcher

  val group = new NioEventLoopGroup()
  val apns = ApnsExt(system).connection[Int](
    Environment.Development,
    loadPkcs12FromResource("/cert.p12", "password"),
    group)

  val deviceToken = DeviceToken("64-chars hex string")

  val payload = Payload.Builder()
    .withAlert("Hello!")
    .withBadge(1)

  Source.single(1 → Notification(deviceToken, payload))
    .via(apns)
    .runForeach(println)
    .onComplete { _ ⇒
      group.shutdownGracefully()
      system.terminate()
    }
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
  .withCategory("ACCEPT_IDENTIFIER")
  .withCustomField("acme1", "bar")
  .withCustomField("acme2", 42)
```

## System Requirements

Akka-stream-apns works with Java 8 and newer.

HTTP/2 over TLS requires the use of ALPN. Java, however, currently does not
support ALPN. As akka-stream-apns internally uses [Netty's](http://netty.io)
`codec-http2` to connect to APNs servers, there are two options how to do TLS:

 * [TLS with OpenSSL](http://netty.io/wiki/requirements-for-4.x.html#tls-with-openssl)
 * [TLS with JDK + Jetty-ALPN](http://netty.io/wiki/requirements-for-4.x.html#tls-with-jdk-jetty-alpnnpn)

    Add `Xbootclasspath` JVM option with the path to the Jetty `alpn-boot` jar.
    The [Jetty-ALPN jar](http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions)
    is specific to the version of Java you are using.

    ```
    java -Xbootclasspath/p:/path/to/jetty/alpn/extension.jar ...
    ```

## Limitations

* No reconnect/resend on error (yet)

## Consulting

Our professional consulting services are highly flexible to address specific issues such as troubleshooting or other services tailored to suit your needs.

Please [contact us](mailto:info@reactivehub.com) for more information.
