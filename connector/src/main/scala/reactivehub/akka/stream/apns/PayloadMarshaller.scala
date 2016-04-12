package reactivehub.akka.stream.apns

import akka.util.ByteString
import scala.language.higherKinds

/**
  * Writes notification payload to a JSON string.
  */
trait PayloadMarshaller {
  type Node
  type Writer[_]
  def jsonString(value: String): Node
  def jsonNumber(value: Int): Node
  def jsonArray(elements: Seq[Node]): Node
  def jsonObject(fields: Map[String, Node]): Node
  def write[T](t: T, writer: Writer[T]): Node
  def print(value: Node): ByteString
}

object PayloadMarshaller {
  type Aux[N, W[_]] = PayloadMarshaller { type Node = N; type Writer[T] = W[T] }
}
