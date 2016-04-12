package reactivehub.akka.stream.apns

import akka.actor.{ExtendedActorSystem, Extension, ExtensionKey}
import akka.stream.scaladsl._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.ssl.SslContext
import java.net.InetSocketAddress
import scala.concurrent.Future

class ApnsExt(implicit system: ExtendedActorSystem) extends Extension {
  /**
    * Creates a Flow representing a prospective connection to APNs.
    */
  def connection[T](host: String, port: Int, sslContext: SslContext,
    group: NioEventLoopGroup)(implicit ru: ResponseUnmarshaller): Flow[(T, Notification), (T, Response), Future[Option[Reason]]] =
    connection(new InetSocketAddress(host, port), sslContext, group)

  /**
    * Creates a Flow representing a prospective connection to APNs.
    */
  def connection[T](remoteAddress: InetSocketAddress, sslContext: SslContext,
    group: NioEventLoopGroup)(implicit ru: ResponseUnmarshaller): Flow[(T, Notification), (T, Response), Future[Option[Reason]]] =
    Flow.fromGraph(NioApnsClientStage(remoteAddress, sslContext, group))
}

object ApnsExt extends ExtensionKey[ApnsExt]
