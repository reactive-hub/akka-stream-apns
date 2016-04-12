package reactivehub.akka.stream.apns

import akka.util.ByteString
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig._
import io.netty.handler.ssl._
import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory ⇒ KMF, TrustManagerFactory ⇒ TMF}
import scala.util.control.NonFatal

/**
  * Provides helpers for creating a SslContext from PKCS 12.
  */
object TlsUtil {
  def loadPkcs12(pkcs12: ByteString): SslContext = loadPkcs12(pkcs12, None)

  def loadPkcs12(pkcs12: ByteString, password: String): SslContext =
    loadPkcs12(pkcs12, Option(password))

  def loadPkcs12(pkcs12: ByteString, password: Option[String] = None): SslContext =
    loadPkcs12(pkcs12.iterator.asInputStream, password)

  def loadPkcs12(stream: InputStream): SslContext = loadPkcs12(stream, None)

  def loadPkcs12(stream: InputStream, password: String): SslContext =
    loadPkcs12(stream, Option(password))

  def loadPkcs12(stream: InputStream, password: Option[String]): SslContext = {
    val pwdChars = password.map(_.toCharArray).orNull
    val clientStore = KeyStore.getInstance("PKCS12")
    clientStore.load(stream, pwdChars)
    val keyManagerFactory = KMF.getInstance(KMF.getDefaultAlgorithm)
    keyManagerFactory.init(clientStore, pwdChars)
    val trustManagerFactory = TMF.getInstance(TMF.getDefaultAlgorithm)
    trustManagerFactory.init(null: KeyStore)

    val provider =
      if (OpenSsl.isAlpnSupported) SslProvider.OPENSSL else SslProvider.JDK

    SslContextBuilder.forClient()
      .sslProvider(provider)
      .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
      .trustManager(trustManagerFactory)
      .keyManager(keyManagerFactory)
      .clientAuth(ClientAuth.REQUIRE)
      .applicationProtocolConfig(new ApplicationProtocolConfig(
        Protocol.ALPN,
        SelectorFailureBehavior.NO_ADVERTISE,
        SelectedListenerFailureBehavior.ACCEPT,
        ApplicationProtocolNames.HTTP_2))
      .build()
  }

  def loadPkcs12FromResource(name: String): SslContext =
    loadPkcs12FromResource(name, None)

  def loadPkcs12FromResource(name: String, password: String): SslContext =
    loadPkcs12FromResource(name, Option(password))

  def loadPkcs12FromResource(name: String, password: Option[String]): SslContext = {
    val stream = TlsUtil.getClass.getResourceAsStream(name)
    try loadPkcs12(stream, password)
    finally {
      try stream.close()
      catch {
        case NonFatal(e) ⇒ e.printStackTrace()
      }
    }
  }
}
