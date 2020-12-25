package com.iscs.imgwriter.domains

import java.awt.{Color, Graphics, Graphics2D}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import cats.effect.{Concurrent, ConcurrentEffect}
import cats.implicits._
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import fs2.Stream
import javax.imageio.ImageIO
import org.http4s.Uri
import org.log4s.getLogger

class ImageWriterService[F[_]: Concurrent](image: List[BufferedImage])(implicit F: ConcurrentEffect[F]) {
  private val L = getLogger
  private val xOffset = 85
  private val yOffset = 85
  private val fontSize = 16f
  private val delim = "@"
  val numImages: Int = image.size - 1
  private val radians = Math.PI/30.0  // 180/30 -> 6
  private val cosRadians = Math.cos(radians)
  private val sinRadians = Math.sin(radians)
  private val qrDim = 100

  def updateImage(text: String, x: Int, y: Int, index: Int): Stream[F, Byte] = for {
    cloneImage <- Stream.eval(cloneImage(image(Math.min(index, numImages))))
    decodedText <- Stream.eval(Concurrent[F].delay(Uri.decode(text)))
    textImage <- Stream.eval {
      val ndx = LazyList.from(0).iterator
      Concurrent[F].delay(decodedText.split(delim)
        .foldLeft(cloneImage) { case (acc, stringPart) =>
          val count = ndx.next()
          val xAdj = count * xOffset * sinRadians
          withText(acc, stringPart.replaceAll("\\*\\*\\*","@"), xAdj, x, y + yOffset * count)
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

  private def withText(img: BufferedImage, text: String, xAdj: Double, x: Int, y: Int): BufferedImage = {
    def embedText(text: String, gfx2D: Graphics2D, x: Int, y: Int): Unit = {
      gfx2D.setFont(gfx2D.getFont.deriveFont(fontSize))
      gfx2D.setColor(Color.magenta)
      gfx2D.rotate(-radians)
      gfx2D.drawString(text, x, y)
      gfx2D.rotate(radians)
    }

    def embedQR(text: String, gfx2D: Graphics2D, x: Int, y: Int): Unit = {
      val qrCodeWriter = new QRCodeWriter
      val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, qrDim, qrDim)
      val qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix)
      gfx2D.drawImage(qrImage, x, y, null)
    }

    val imgWidth = img.getWidth
    val imgHeight = img.getHeight
    val g = img.getGraphics
    val fontMetrics = g.getFontMetrics(g.getFont.deriveFont(fontSize))
    val stringRect2D = fontMetrics.getStringBounds(text, g)
    val stringHeight = stringRect2D.getHeight
    val stringWidth = stringRect2D.getWidth * cosRadians
    val gfx2D = g.asInstanceOf[Graphics2D]
    if (y + stringHeight < imgHeight &&
        x + stringWidth.toInt < imgWidth) {
      embedText(text, gfx2D, x - xAdj.toInt, y)
      embedQR(text, gfx2D, x - xOffset, y - yOffset)
      gfx2D.dispose()
    } else {
      embedText("Too long", gfx2D, x, y)
    }
    img
  }
}