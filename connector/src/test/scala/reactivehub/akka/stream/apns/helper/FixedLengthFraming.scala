package reactivehub.akka.stream.apns.helper

import akka.stream._
import akka.stream.stage._
import akka.util.ByteString

final class FixedLengthFraming(length: Int)
    extends GraphStage[FlowShape[ByteString, ByteString]] {

  val in = Inlet[ByteString]("FixedLengthFraming.in")
  val out = Outlet[ByteString]("FixedLengthFraming.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var stash = ByteString.empty

      private def pushOrPull(): Unit = {
        if (stash.size >= length) {
          val frame = stash.take(length)
          stash = stash.drop(length)
          push(out, frame)
        } else pull(in)
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          stash ++= grab(in)
          pushOrPull()
        }

        override def onUpstreamFinish(): Unit = {
          emitMultiple(out, stash.grouped(length))
          complete(out)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pushOrPull()
      })
    }
}
