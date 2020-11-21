package com.iscs.imgwriter.domains

import java.awt.{Color, Graphics}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import cats.effect.{Concurrent, ConcurrentEffect}
import cats.implicits._
import fs2.Stream
import javax.imageio.ImageIO
import org.http4s.Uri
import org.log4s.getLogger

class ImageWriterService[F[_]: Concurrent](image: List[BufferedImage])(implicit F: ConcurrentEffect[F]) {
  private val L = getLogger
  private val yOffset = 18
  private val fontSize = 16f
  private val delim = "@"
  private val numImages = image.size - 1

  def updateImage(text: String, x: Int, y: Int, index: Int): Stream[F, Byte] = for {
    cloneImage <- Stream.eval(cloneImage(image(Math.min(index, numImages))))
    decodedText <- Stream.eval(Concurrent[F].delay(Uri.decode(text)))
    textImage <- Stream.eval {
      val ndx = LazyList.from(0).iterator
      Concurrent[F].delay(decodedText.split(delim)
        .foldLeft(cloneImage) { case (acc, stringPart) =>
          withText(acc, stringPart, x, y + yOffset * ndx.next())
        })
    }
    bytes <- imgToByte(textImage)
  } yield bytes

  private def cloneImage(img: BufferedImage): F[BufferedImage] = for {
    cm <- Concurrent[F].delay(img.getColorModel)
    isAlphaPremultiplied <- Concurrent[F].delay(cm.isAlphaPremultiplied)
    raster <- Concurrent[F].delay(img.copyData(img.getRaster.createCompatibleWritableRaster()))
  } yield new BufferedImage(cm, raster, isAlphaPremultiplied, null)

  private def imgToByte(img: BufferedImage): Stream[F, Byte] = for {
    bos <- Stream.eval(Concurrent[F].delay(new ByteArrayOutputStream()))
    _ <- Stream.eval(Concurrent[F].delay(ImageIO.write(img, "jpg", bos)))
    data <- Stream.eval(Concurrent[F].delay(bos.toByteArray))
    s <- Stream.emits(data)
  } yield s

  private def withText(img: BufferedImage, text: String, x: Int, y: Int): BufferedImage = {
    def embedText(text: String, g: Graphics, x: Int, y: Int): Unit =  {
      g.setFont(g.getFont.deriveFont(fontSize))
      g.setColor(Color.magenta)
      g.drawString(text, x, y)
      g.dispose()
    }

    val imgWidth = img.getWidth
    val imgHeight = img.getHeight
    val g = img.getGraphics
    val fontMetrics = g.getFontMetrics(g.getFont.deriveFont(fontSize))
    val stringHeight = fontMetrics.stringWidth(text)
    val stringWidth = fontMetrics.getStringBounds(text, g)
    if (y + stringHeight < imgHeight && x + stringWidth.getWidth.toInt < imgWidth) {
      embedText(text, g, x, y)
    } else {
      embedText("Too long", g, x, y)
    }
    img
  }
}