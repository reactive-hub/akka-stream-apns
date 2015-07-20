package reactivehub.akka.stream.apns

final case class DeviceToken(value: Array[Byte]) {
  require(value.length == 32, "Device token must be a 32-bytes array")
  override def toString: String = value.map("%02X" format _).mkString
}

object DeviceToken {
  private val tokenFmt = """\A\p{XDigit}{64}\z""".r

  def apply(value: String): DeviceToken = {
    require(tokenFmt.findFirstMatchIn(value).isDefined, "Device token must be a 64-chars hex string")
    DeviceToken(value.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toArray)
  }
}
