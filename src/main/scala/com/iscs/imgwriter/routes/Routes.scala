package com.iscs.imgwriter.routes

import cats.effect._
import com.iscs.imgwriter.domains.ImageWriterService
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s._
import org.http4s.dsl.Http4sDsl

import scala.util.Try

object Routes {
  private val L = Logger[this.type]
  private val inputLimit = 5000

  def imageRoutes[F[_]: Sync: Concurrent](I: ImageWriterService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case req @ GET -> Root / "static" / index / txt =>
        if (txt.size > inputLimit)
          Ok("Text overflow")
        else
          Ok(for {
            imageNdx <- Stream.eval(Concurrent[F].delay(Try(index.toInt).toOption.getOrElse(0)))
            _ <- Stream.eval(Concurrent[F].delay(L.info(s""""request" txt=$txt image=$index""")))
            img <- I.updateImage(txt, 70, 70, imageNdx)
          } yield img)
    }
  }
}
