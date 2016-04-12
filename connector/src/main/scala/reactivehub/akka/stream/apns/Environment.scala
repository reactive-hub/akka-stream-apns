package reactivehub.akka.stream.apns

import java.net.InetSocketAddress

object Environment {
  val Production = new InetSocketAddress("api.push.apple.com", 443)
  val Production2197 = new InetSocketAddress("api.push.apple.com", 2197)
  val Development = new InetSocketAddress("api.development.push.apple.com", 443)
  val Development2197 = new InetSocketAddress("api.development.push.apple.com", 2197)
}
