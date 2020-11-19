package com.iscs.imgwriter.routes

import cats.effect._
import com.iscs.imgwriter.domains.ImageWriterService
import com.typesafe.scalalogging.Logger
import org.http4s._
import org.http4s.dsl.Http4sDsl

object Routes {
  private val L = Logger[this.type]

  def imageRoutes[F[_]: Sync](I: ImageWriterService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}
    import dsl._
    HttpRoutes.of[F] {
      case req @ GET -> Root / "static" / txt =>
        Ok(for {
          img <- I.updateImage(txt, 100, 100)
        } yield img)
    }
  }
}
