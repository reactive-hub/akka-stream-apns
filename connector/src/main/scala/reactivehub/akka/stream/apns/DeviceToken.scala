package reactivehub.akka.stream.apns

import java.lang.Integer.parseInt
import reactivehub.akka.stream.apns.DeviceToken.Digits

/**
  * APNs device token. Tokens are either 32 or 100 bytes arrays.
  */
final case class DeviceToken(bytes: List[Byte]) {
  require(
    bytes.length == 32 || bytes.length == 100,
    "Device token must be a 32 or 100 bytes array")

  override def toString: String =
    bytes.foldLeft(new StringBuilder) {
      case (sb, b) ⇒ sb.append(Digits((b & 0xF0) >> 4)).append(Digits(b & 0x0F))
    }.toString
}

object DeviceToken {
  private val Digits = "0123456789ABCDEF"

  private val fmt = """\A(?:\p{XDigit}\p{XDigit})+\z""".r

  def apply(str: String): DeviceToken = {
    require(
      (str.length == 64 || str.length == 200) && fmt.findFirstIn(str).isDefined,
      "Device token must be a 64 or 200 chars hex string")
    new DeviceToken(str.sliding(2, 2).map(parseInt(_, 16).toByte).toList)
  }
}
