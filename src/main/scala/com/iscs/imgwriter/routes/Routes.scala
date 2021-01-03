package com.iscs.imgwriter.routes

import cats.effect._
import com.iscs.imgwriter.domains.ImageWriterService
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.{CORS, CORSConfig}
import scala.concurrent.duration._

import scala.util.Try

object Routes {
  private val L = Logger[this.type]
  private val inputLimit = 5000
  private val topicLimit = 128
  private val topLeftX = 100
  private val topLeftY = 180

  private val reactOrigin = "http://localhost:3000"
  private val reactDeploys = List("http://localhost",
    "https://localhost",
    "http://berne.iscs-i.com",
    "https://berne.iscs-i.com",
    "https://192.168.4.46",
    "http://192.168.4.46")
  private val methods = Set("GET")
  private def checkOrigin(origin: String): Boolean =
    allowedOrigins.contains(origin)
  private val allowedOrigins = Set(reactOrigin) ++ reactDeploys
  private val methodConfig = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = checkOrigin
  )

  def imageRoutes[F[_]: Sync: Concurrent](I: ImageWriterService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case req @ GET -> Root / "img" / index / topic / link =>
        if (link.size > inputLimit || topic.size > topicLimit)
          Ok("Text overflow")
        else
          Ok(for {
            imageNdx <- Stream.eval(Concurrent[F].delay(Try(index.toInt).toOption.getOrElse(I.numImages)))
            _ <- Stream.eval(Concurrent[F].delay(L.info(s""""request" txt=$link image=$index""")))
            img <- I.updateImage(link, topic, topLeftX, topLeftY, imageNdx)
          } yield img)
    }
    CORS(service, methodConfig)
  }
}
