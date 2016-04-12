package reactivehub.akka.stream.apns

import org.scalatest.{FlatSpec, Matchers}
import reactivehub.akka.stream.apns.DeviceTokenSpec._

class DeviceTokenSpec extends FlatSpec with Matchers {
  "DeviceToken" should "create a token for a 32 byte array" in {
    DeviceToken(list32).toString shouldBe token32
  }

  it should "parse a valid 64 chars hex string" in {
    DeviceToken(token32).bytes shouldBe list32
  }

  it should "create a token for a 100 byte array" in {
    DeviceToken(list100).toString shouldBe token100
  }

  it should "parse a valid 200 chars hex string" in {
    DeviceToken(token100).bytes shouldBe list100
  }

  it should "fail to create a token for an array of a length not in {32, 100}" in {
    intercept[IllegalArgumentException] {
      DeviceToken(List[Byte](1, 2, 3))
    }
  }

  it should "fail to parse a string of a length not in {64, 200}" in {
    intercept[IllegalArgumentException] {
      DeviceToken("010203")
    }
  }

  it should "fail to parse a non-hexadecimal string" in {
    intercept[IllegalArgumentException] {
      DeviceToken(nonHexa)
    }
  }
}

object DeviceTokenSpec {
  val list32 = List[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
    17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)

  val list100 = List[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34,
    35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
    54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72,
    73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91,
    92, 93, 94, 95, 96, 97, 98, 99, 100)

  val token32 = "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20"

  val token100 = "0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F2" +
    "02122232425262728292A2B2C2D2E2F303132333435363738393A3B3C3D3E3F4041424344454" +
    "64748494A4B4C4D4E4F505152535455565758595A5B5C5D5E5F6061626364"

  val nonHexa = "0102030405060708090G0H0I0J0K0L101112131415161718191G1H1I1J1K1L20"
}
