package reactivehub.akka.stream.apns

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import reactivehub.akka.stream.apns.DeviceTokenBenchmark._

object DeviceTokenBenchmark {
  val Digits = "0123456789ABCDEF"

  val tokenBytes = List[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
}

@BenchmarkMode(Array(Mode.Throughput))
@Warmup(time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(time = 10, timeUnit = TimeUnit.SECONDS)
class DeviceTokenBenchmark {
  @Benchmark
  def testStringFormat: String =
    tokenBytes.map("%02X" format _).mkString

  @Benchmark
  def testBuilder: String =
    tokenBytes.foldLeft(new StringBuilder) {
      case (sb, b) ⇒ sb.append(Digits((b & 0xF0) >> 4)).append(Digits(b & 0x0F))
    }.toString
}
