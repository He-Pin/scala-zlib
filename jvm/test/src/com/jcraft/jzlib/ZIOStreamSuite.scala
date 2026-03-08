package com.jcraft.jzlib

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import JZlib._

@annotation.nowarn("cat=deprecation")
class ZIOStreamSuite extends munit.FunSuite {

  test("ZOutputStream compress and ZInputStream decompress") {
    val hello = "Hello World!"

    val out    = new ByteArrayOutputStream()
    val zOut   = new ZOutputStream(out, Z_BEST_COMPRESSION)
    val objOut = new ObjectOutputStream(zOut)
    objOut.writeObject(hello)
    zOut.close()

    val in    = new ByteArrayInputStream(out.toByteArray)
    val zIn   = new ZInputStream(in)
    val objIn = new ObjectInputStream(zIn)

    assertEquals(objIn.readObject().toString, hello)
  }

  test("ZOutputStream and ZInputStream nowrap support") {
    val hello = "Hello World!".getBytes

    val baos = new ByteArrayOutputStream()
    val zos  = new ZOutputStream(baos, Z_DEFAULT_COMPRESSION, true)
    zos.write(hello)
    zos.close()

    val baos2 = new ByteArrayOutputStream()
    val zis   = new ZInputStream(new ByteArrayInputStream(baos.toByteArray), true)
    val buf   = new Array[Byte](100)
    var n     = zis.read(buf)
    while (n != -1) {
      baos2.write(buf, 0, n)
      n = zis.read(buf)
    }
    zis.close()
    val data2 = baos2.toByteArray

    assertEquals(data2.length, hello.length)
    assertEquals(data2.toSeq, hello.toSeq)
  }

  test("ZOutputStream compress and ZInputStream decompress with large data") {
    val rng  = new scala.util.Random(42)
    val data = Array.fill[Byte](10000)(rng.nextInt(256).toByte)

    val baos = new ByteArrayOutputStream()
    val zos  = new ZOutputStream(baos, Z_DEFAULT_COMPRESSION)
    zos.write(data)
    zos.close()

    val baos2  = new ByteArrayOutputStream()
    val zis    = new ZInputStream(new ByteArrayInputStream(baos.toByteArray))
    val buf    = new Array[Byte](512)
    var n      = zis.read(buf)
    while (n != -1) {
      baos2.write(buf, 0, n)
      n = zis.read(buf)
    }
    zis.close()
    val result = baos2.toByteArray

    assertEquals(result.length, data.length)
    assertEquals(result.toSeq, data.toSeq)
  }
}
