package com.iscs.imgwriter

import java.awt.image.BufferedImage

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import com.typesafe.scalalogging.Logger
import fs2.Stream

object Main extends IOApp {
  private val L = Logger[this.type]
  private val image1 = "image1.jpg"
  private val image2 = "image2.jpg"
  private val image3 = "image3.jpg"
  private val image4 = "image4.jpg"

  private val port = 8080
  private val listener = "0.0.0.0"

  def getImageStream(imgFile: String): Stream[IO, BufferedImage] =
    Stream.eval(Blocker[IO].use { blocker =>
      Server.getResource[IO](imgFile, blocker)
    }).handleErrorWith { ex =>
      L.error(""""Could not read {}" ex={}""", imgFile, ex.toString)
      Stream.empty
    }

  def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO.delay(System.currentTimeMillis)
    serverStream = for {
        resImage1 <- getImageStream(image1)
        resImage2 <- getImageStream(image2)
        resImage3 <- getImageStream(image3)
        resImage4 <- getImageStream(image4)

      str <- Server.stream[IO](List(resImage1, resImage2, resImage3, resImage4), port, listener)
    } yield str

    s <- serverStream
      .compile.drain.as(ExitCode.Success)
      .handleErrorWith(ex => IO {
        L.error("\"exception during stream startup\" exception={} ex={}", ex.toString, ex)
        ExitCode.Error
      })
  } yield s
}