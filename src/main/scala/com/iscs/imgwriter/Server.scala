package com.iscs.imgwriter

import java.awt.image.BufferedImage
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.implicits._
import com.iscs.imgwriter.domains.ImageWriterService
import com.iscs.imgwriter.routes.Routes.imageRoutes
import com.iscs.imgwriter.util.ResourceProcessor
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => hpLogger}

object Server {

  private val L = Logger[this.type]

  def getResource[F[_]: Sync: ContextShift: Timer: ConcurrentEffect](resName: String, blocker: Blocker): F[BufferedImage] =
    for {
      resProc <- Concurrent[F].delay(new ResourceProcessor(resName))
      image <- resProc.readImageFromFile(blocker)
    } yield image

  def stream[F[_]: ConcurrentEffect](image: List[BufferedImage], port: Int, listener: String)(implicit T: Timer[F], Con: ContextShift[F]): Stream[F, Nothing] = {
    val srvStream = for {
      imgWriter <- Stream.eval(Concurrent[F].delay(new ImageWriterService[F](image)))
      httpApp <- Stream.eval(Concurrent[F].delay(imageRoutes[F](imgWriter).orNotFound))
      finalHttpApp <- Stream.eval(Concurrent[F].delay(hpLogger.httpApp(logHeaders = true, logBody = true)(httpApp)))

      exitCode <- BlazeServerBuilder[F]
        .bindHttp(port, listener)
        .withHttpApp(finalHttpApp)
        .serve
    } yield exitCode
    srvStream.drain
  }
}