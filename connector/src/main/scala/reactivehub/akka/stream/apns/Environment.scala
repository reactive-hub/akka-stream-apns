package reactivehub.akka.stream.apns

import java.net.InetSocketAddress

sealed trait Environment {
  def gateway: InetSocketAddress
  def feedback: InetSocketAddress
}

object Environment {
  case object Production extends Environment {
    override val gateway = new InetSocketAddress("gateway.push.apple.com", 2195)
    override val feedback = new InetSocketAddress("feedback.push.apple.com", 2196)
  }

  case object Sandbox extends Environment {
    override val gateway = new InetSocketAddress("gateway.sandbox.push.apple.com", 2195)
    override val feedback = new InetSocketAddress("feedback.sandbox.push.apple.com", 2196)
  }
}
