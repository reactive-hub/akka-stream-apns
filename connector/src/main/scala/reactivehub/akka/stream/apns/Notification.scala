package reactivehub.akka.stream.apns

import akka.util.ByteString
import java.util.UUID
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration._
import scala.language.{higherKinds, implicitConversions}

/**
  * APNs notification model.
  */
final case class Notification(
  deviceToken: DeviceToken,
  payload: Payload,
  id: Option[UUID] = None,
  expiration: Option[Expiration] = None,
  priority: Option[Priority] = None,
  topic: Option[String] = None)

object Notification {
  def apply(deviceToken: DeviceToken, payload: Payload, id: UUID): Notification =
    Notification(deviceToken, payload, id = Some(id))
}

final case class Payload(value: ByteString) {
  def size: Int = value.size
  def toArray: Array[Byte] = value.toArray[Byte]
  override def toString: String = value.utf8String
}

object Payload {
  val empty = Payload(ByteString("{\"aps\":{}}"))

  implicit def forBuilder[B, N, W[_]](b: B)(implicit ev: B ⇒ Builder[N, W]): Payload = b.result

  sealed trait Builder[N, W[_]] {
    def withAlert(alert: String): Builder[N, W]
    def withLocalizedAlert(key: String, args: String*): Builder[N, W]
    def withTitle(title: String): Builder[N, W]
    def withLocalizedTitle(key: String, args: String*): Builder[N, W]
    def withLocalizedAction(key: String): Builder[N, W]
    def withLaunchImage(launchImage: String): Builder[N, W]
    def withBadge(badge: Int): Builder[N, W]
    def withSound(sound: String): Builder[N, W]
    def withContentAvailable: Builder[N, W]
    def withCategory(category: String): Builder[N, W]
    def withCustomField[T](key: String, value: T)(implicit writer: W[T]): Builder[N, W]
    def result: Payload
  }

  object Builder {
    def apply()(implicit m: PayloadMarshaller): Builder[m.Node, m.Writer] =
      new EmptyBuilder[m.Node, m.Writer](m)

    implicit def forString(alert: String)(implicit m: PayloadMarshaller): Builder[m.Node, m.Writer] =
      new SimpleBuilder[m.Node, m.Writer](m, alert = Some(alert))
  }

  private[apns] case class EmptyBuilder[N, W[_]](marshaller: PayloadMarshaller.Aux[N, W]) extends Builder[N, W] {
    override def withAlert(alert: String): Builder[N, W] = SimpleBuilder[N, W](marshaller, alert = Some(alert))

    override def withLocalizedAlert(key: String, args: String*): Builder[N, W] =
      CompBuilder[N, W](marshaller, locKey = Some(key), locArgs = Some(args))

    override def withTitle(title: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, title = Some(title))

    override def withLocalizedTitle(key: String, args: String*): Builder[N, W] =
      CompBuilder[N, W](marshaller, titleLocKey = Some(key), titleLocArgs = Some(args))

    override def withLocalizedAction(key: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, actionLocKey = Some(key))

    override def withLaunchImage(launchImage: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, launchImage = Some(launchImage))

    override def withBadge(badge: Int): Builder[N, W] = SimpleBuilder[N, W](marshaller, badge = Some(badge))

    override def withSound(sound: String): Builder[N, W] = SimpleBuilder[N, W](marshaller, sound = Some(sound))

    override def withContentAvailable: Builder[N, W] = SimpleBuilder[N, W](marshaller, contentAvailable = Some(1))

    override def withCategory(category: String): Builder[N, W] = SimpleBuilder[N, W](marshaller, category = Some(category))

    override def withCustomField[T](key: String, value: T)(implicit writer: W[T]): Builder[N, W] =
      SimpleBuilder[N, W](marshaller, customFields = Map(key → marshaller.write(value, writer)))

    override def result: Payload = {
      val json = marshaller.jsonObject(Map("aps" → marshaller.jsonObject(Map.empty)))
      Payload(marshaller.print(json))
    }
  }

  private[apns] case class SimpleBuilder[N, W[_]](
    marshaller: PayloadMarshaller.Aux[N, W],
    alert: Option[String] = None,
    badge: Option[Int] = None,
    sound: Option[String] = None,
    contentAvailable: Option[Int] = None,
    category: Option[String] = None,
    customFields: Map[String, N] = Map.empty[String, N])
      extends Builder[N, W] {

    override def withAlert(alert: String): Builder[N, W] = copy(alert = Some(alert))

    override def withLocalizedAlert(key: String, args: String*): Builder[N, W] =
      CompBuilder[N, W](marshaller, body = alert, locKey = Some(key), locArgs = Some(args),
        badge = badge, sound = sound, contentAvailable = contentAvailable, category = category, customFields = customFields)

    override def withTitle(title: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, body = alert, title = Some(title), badge = badge,
        sound = sound, contentAvailable = contentAvailable, category = category, customFields = customFields)

    override def withLocalizedTitle(key: String, args: String*): Builder[N, W] =
      CompBuilder[N, W](marshaller, body = alert, titleLocKey = Some(key), titleLocArgs = Some(args),
        badge = badge, sound = sound, contentAvailable = contentAvailable, category = category, customFields = customFields)

    override def withLocalizedAction(key: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, body = alert, actionLocKey = Some(key), badge = badge,
        sound = sound, contentAvailable = contentAvailable, category = category, customFields = customFields)

    override def withLaunchImage(launchImage: String): Builder[N, W] =
      CompBuilder[N, W](marshaller, body = alert, launchImage = Some(launchImage), badge = badge,
        sound = sound, contentAvailable = contentAvailable, category = category, customFields = customFields)

    override def withBadge(badge: Int): Builder[N, W] = copy(badge = Some(badge))

    override def withSound(sound: String): Builder[N, W] = copy(sound = Some(sound))

    override def withContentAvailable: Builder[N, W] = copy(contentAvailable = Some(1))

    override def withCategory(category: String): Builder[N, W] = copy(category = Some(category))

    override def withCustomField[T](key: String, value: T)(implicit writer: W[T]): Builder[N, W] =
      copy(customFields = customFields.updated(key, marshaller.write(value, writer)))

    override def result: Payload = {
      val aps = Map(
        "alert" → alert.map(marshaller.jsonString),
        "badge" → badge.map(marshaller.jsonNumber),
        "sound" → sound.map(marshaller.jsonString),
        "content-available" → contentAvailable.map(marshaller.jsonNumber),
        "category" → category.map(marshaller.jsonString)).filter(_._2.isDefined).mapValues(_.get)

      val json = marshaller.jsonObject(customFields ++ Map("aps" → marshaller.jsonObject(aps)))
      Payload(marshaller.print(json))
    }
  }

  private[apns] case class CompBuilder[N, W[_]](
    marshaller: PayloadMarshaller.Aux[N, W],
    title: Option[String] = None,
    body: Option[String] = None,
    titleLocKey: Option[String] = None,
    titleLocArgs: Option[Seq[String]] = None,
    actionLocKey: Option[String] = None,
    locKey: Option[String] = None,
    locArgs: Option[Seq[String]] = None,
    launchImage: Option[String] = None,
    badge: Option[Int] = None,
    sound: Option[String] = None,
    contentAvailable: Option[Int] = None,
    category: Option[String] = None,
    customFields: Map[String, N] = Map.empty[String, N])
      extends Builder[N, W] {

    override def withAlert(alert: String): Builder[N, W] = copy(body = Some(alert))

    override def withLocalizedAlert(key: String, args: String*): Builder[N, W] =
      copy(locKey = Some(key), locArgs = Some(args))

    override def withTitle(title: String): Builder[N, W] = copy(title = Some(title))

    override def withLocalizedTitle(key: String, args: String*): Builder[N, W] =
      copy(titleLocKey = Some(key), titleLocArgs = Some(args))

    override def withLocalizedAction(key: String): Builder[N, W] = copy(actionLocKey = Some(key))

    override def withLaunchImage(launchImage: String): Builder[N, W] = copy(launchImage = Some(launchImage))

    override def withBadge(badge: Int): Builder[N, W] = copy(badge = Some(badge))

    override def withSound(sound: String): Builder[N, W] = copy(sound = Some(sound))

    override def withContentAvailable: Builder[N, W] = copy(contentAvailable = Some(1))

    override def withCategory(category: String): Builder[N, W] = copy(category = Some(category))

    override def withCustomField[T](key: String, value: T)(implicit writer: W[T]): Builder[N, W] =
      copy(customFields = customFields.updated(key, marshaller.write(value, writer)))

    override def result: Payload = {
      val alert = Map(
        "title" → title.map(marshaller.jsonString),
        "body" → body.map(marshaller.jsonString),
        "title-loc-key" → titleLocKey.map(marshaller.jsonString),
        "title-loc-args" → titleLocArgs.map(s ⇒ marshaller.jsonArray(s.map(marshaller.jsonString))),
        "action-loc-key" → actionLocKey.map(marshaller.jsonString),
        "loc-key" → locKey.map(marshaller.jsonString),
        "loc-args" → locArgs.map(s ⇒ marshaller.jsonArray(s.map(marshaller.jsonString))),
        "launch-image" → launchImage.map(marshaller.jsonString)).filter(_._2.isDefined).mapValues(_.get)

      val aps = Map(
        "alert" → Some(marshaller.jsonObject(alert)),
        "badge" → badge.map(marshaller.jsonNumber),
        "sound" → sound.map(marshaller.jsonString),
        "content-available" → contentAvailable.map(marshaller.jsonNumber),
        "category" → category.map(marshaller.jsonString)).filter(_._2.isDefined).mapValues(_.get)

      val json = marshaller.jsonObject(customFields ++ Map("aps" → marshaller.jsonObject(aps)))
      Payload(marshaller.print(json))
    }
  }
}

final case class Expiration(value: Long) {
  override def toString: String = value.toString
}

object Expiration {
  implicit def apply(duration: Duration): Expiration = duration match {
    case d: FiniteDuration ⇒ Expiration((System.nanoTime().nanos + d).toSeconds)
    case _: Infinite       ⇒ Expiration(0)
  }

  implicit def forInt(value: Int): Expiration = Expiration(value.toLong)
  implicit def forLong(value: Long): Expiration = Expiration(value)
}

sealed trait Priority

object Priority {
  case object High extends Priority
  case object Low extends Priority
}
