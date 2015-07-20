package reactivehub.akka.stream.apns

import akka.util.ByteString
import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import scala.util.control.NonFatal

object TlsUtil {
  def loadPkcs12(pkcs12: ByteString): SSLContext = loadPkcs12(pkcs12, None)

  def loadPkcs12(pkcs12: ByteString, password: String): SSLContext = loadPkcs12(pkcs12, Option(password))

  def loadPkcs12(pkcs12: ByteString, password: Option[String]): SSLContext =
    loadPkcs12(pkcs12.iterator.asInputStream, password)

  def loadPkcs12(stream: InputStream): SSLContext = loadPkcs12(stream, None)

  def loadPkcs12(stream: InputStream, password: String): SSLContext = loadPkcs12(stream, Option(password))

  def loadPkcs12(stream: InputStream, password: Option[String]): SSLContext = {
    val pwdChars = password.map(_.toCharArray).orNull
    val clientStore = KeyStore.getInstance("PKCS12")
    clientStore.load(stream, pwdChars)
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(clientStore, pwdChars)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(null: KeyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  def loadPkcs12FromResource(name: String): SSLContext = loadPkcs12FromResource(name, None)

  def loadPkcs12FromResource(name: String, password: String): SSLContext = loadPkcs12FromResource(name, Option(password))

  def loadPkcs12FromResource(name: String, password: Option[String]): SSLContext = {
    val stream = TlsUtil.getClass.getResourceAsStream(name)
    try loadPkcs12(stream, password) finally { try stream.close() catch { case NonFatal(e) ⇒ e.printStackTrace() } }
  }
}
