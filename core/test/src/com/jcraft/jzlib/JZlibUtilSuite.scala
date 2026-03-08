package com.jcraft.jzlib

import JZlib._

class JZlibUtilSuite extends munit.FunSuite {

  test("deflateBound returns reasonable upper bound") {
    // For small data
    assert(deflateBound(100) >= 100)
    // For empty data
    assert(deflateBound(0) == 13)
    // For 1MB
    assert(deflateBound(1024 * 1024) > 1024 * 1024)
  }

  test("compressBound equals deflateBound") {
    assertEquals(compressBound(1000), deflateBound(1000))
    assertEquals(compressBound(0), deflateBound(0))
  }

  test("compress and uncompress round-trip") {
    val original = "Hello, World! This is a test of one-shot compression.".getBytes("UTF-8")
    val compressed = compress(original)
    val decompressed = uncompress(compressed)
    assertEquals(new String(decompressed, "UTF-8"), new String(original, "UTF-8"))
  }

  test("compress and uncompress with large data") {
    val original = Array.fill[Byte](100000)((scala.util.Random.nextInt(256) - 128).toByte)
    val compressed = compress(original)
    val decompressed = uncompress(compressed)
    assert(original.sameElements(decompressed))
  }

  test("compress produces smaller output for compressible data") {
    val original = Array.fill[Byte](10000)(42)
    val compressed = compress(original)
    assert(
      compressed.length < original.length,
      s"Expected compressed (${compressed.length}) < original (${original.length})"
    )
  }

  test("compressed size within deflateBound") {
    val original = "Test data for bound verification".getBytes("UTF-8")
    val compressed = compress(original)
    assert(compressed.length <= deflateBound(original.length.toLong))
  }

  test("uncompress throws on invalid data") {
    intercept[ZStreamException] {
      uncompress(Array[Byte](1, 2, 3, 4, 5))
    }
  }
}
