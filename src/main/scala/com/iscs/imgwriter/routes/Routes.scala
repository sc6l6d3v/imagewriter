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
  private val topLeftX = 100
  private val topLeftY = 180

  private val reactOrigin = "http://localhost:3000"
  private val reactApache = "https://localhost"
  private val methods = Set("GET")
  private val methodConfig = CORSConfig(
    anyOrigin = false,
    allowCredentials = true,
    maxAge = 1.day.toSeconds,
    allowedMethods = Some(methods),
    allowedOrigins = Set(reactOrigin, reactApache)
  )

  def imageRoutes[F[_]: Sync: Concurrent](I: ImageWriterService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    val service = HttpRoutes.of[F] {
      case req @ GET -> Root / "static" / index / txt =>
        if (txt.size > inputLimit)
          Ok("Text overflow")
        else
          Ok(for {
            imageNdx <- Stream.eval(Concurrent[F].delay(Try(index.toInt).toOption.getOrElse(I.numImages)))
            _ <- Stream.eval(Concurrent[F].delay(L.info(s""""request" txt=$txt image=$index""")))
            img <- I.updateImage(txt, topLeftX, topLeftY, imageNdx)
          } yield img)
    }
    CORS(service, methodConfig)
  }
}
