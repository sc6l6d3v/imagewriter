package com.iscs.imgwriter

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import com.iscs.imgwriter.routes.Routes
import com.typesafe.scalalogging.Logger
import fs2.Stream
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{Logger => hpLogger}

object Server {

  private val L = Logger[this.type]

  def stream[F[_]: ConcurrentEffect]()(implicit T: Timer[F], Con: ContextShift[F]): Stream[F, Nothing] =
    Stream.resource(Blocker[F]).flatMap { blocker =>
      val imgWriter = new domains.ImageWriterService[F]()

      // Combine Service Routes into an HttpApp.
      // Can also be done via a Router if you
      // want to extract a segments not checked
      // in the underlying routes.
      val httpApp = Routes.imageRoutes[F](imgWriter).orNotFound

      // With Middlewares in place
      val finalHttpApp = hpLogger.httpApp(logHeaders = true, logBody = true)(httpApp)

      val srvStream = for {
        exitCode <- BlazeServerBuilder[F]
          .bindHttp(8080, "0.0.0.0")
          .withHttpApp(finalHttpApp)
          .serve
      } yield exitCode
      srvStream.drain
  }
}