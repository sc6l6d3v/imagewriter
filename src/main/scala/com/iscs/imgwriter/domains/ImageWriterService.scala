package com.iscs.imgwriter.domains

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, IOException}
import java.net.URL

import cats.effect.{Concurrent, ConcurrentEffect}
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream
import org.log4s.getLogger
import javax.imageio.ImageIO
import org.http4s.Uri

class ImageWriterService[F[_]: Concurrent](implicit F: ConcurrentEffect[F]) {
  private val L = getLogger
  private val lenaImage = "http://kevinrye.net/index_files/lenna-so0308derberg-1972_620.jpg"
  private val dummyImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
  private val yOffset = 30

  def updateImage(text: String, x: Int, y: Int): Stream[F, Byte] = for {
    maybeImage <- Stream.eval(getImage(lenaImage))
    decodedText <- Stream.eval(Concurrent[F].delay(Uri.decode(text)))
    textImage <- Stream.eval(
      maybeImage match {
        case Some(img) =>
          val ndx = LazyList.from(0).iterator
          Concurrent[F].delay(decodedText.split("@")
            .foldLeft(img){case (acc, stringPart) =>
              withText(acc, stringPart, x, y + yOffset * ndx.next)
          })
        case None      => Concurrent[F].delay(dummyImage)
      }
    )
    bytes <- imgToByte(textImage)
  } yield bytes

  private def getImage(file: String): F[Option[BufferedImage]] = for {
    url <- Concurrent[F].delay(new URL(file))
    image <- Concurrent[F].delay{
      try {
        Some(ImageIO.read(url))
      } catch {
        case ex: IOException =>
          L.error(s""""Could not read URL" url=$url msg=${ex.getMessage}""")
          None
      }
    }
  } yield image

  private def imgToByte(img: BufferedImage): Stream[F, Byte] = for {
    bos <- Stream.eval(Concurrent[F].delay(new ByteArrayOutputStream()))
    _ <- Stream.eval(Concurrent[F].delay(ImageIO.write(img, "jpg", bos)))
    data <- Stream.eval(Concurrent[F].delay(bos.toByteArray))
    s <- Stream.emits(data)
  } yield s

  private def withText(img: BufferedImage, text: String, x: Int, y: Int): BufferedImage = {
    val g = img.getGraphics
    g.setFont(g.getFont.deriveFont(30f))
    g.drawString(text, x, y)
    g.dispose()
    img
  }
}