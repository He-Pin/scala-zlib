package com.jcraft.jzlib

import JZlib._

class DeflateGetDictionarySuite extends munit.FunSuite {

  test("getDictionary returns sliding window content after compression") {
    val data  = "The quick brown fox jumps over the lazy dog".getBytes
    val compr = new Array[Byte](40000)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_SYNC_FLUSH)
    assertEquals(err, Z_OK)

    val dictionary = new Array[Byte](32768)
    val dictLength = new Array[Int](1)
    err = deflater.getDictionary(dictionary, dictLength)
    assertEquals(err, Z_OK)
    assert(dictLength(0) > 0, "dictionary length should be positive")
    assert(dictLength(0) <= 32768, "dictionary length should not exceed window size")

    // The dictionary should end with the input data we just compressed
    val dictContent = new Array[Byte](dictLength(0))
    System.arraycopy(dictionary, 0, dictContent, 0, dictLength(0))
    val dictStr     = new String(dictContent)
    assert(dictStr.contains("quick brown fox"), s"dictionary should contain input data, got: $dictStr")

    deflater.end()
  }

  test("round-trip: setDictionary, compress, getDictionary matches") {
    val dict  = "repeated pattern repeated pattern repeated".getBytes
    val data  = "repeated pattern again and again".getBytes
    val compr = new Array[Byte](40000)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    err = deflater.setDictionary(dict, dict.length)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_SYNC_FLUSH)
    assertEquals(err, Z_OK)

    val retrievedDict = new Array[Byte](32768)
    val dictLength    = new Array[Int](1)
    err = deflater.getDictionary(retrievedDict, dictLength)
    assertEquals(err, Z_OK)
    assert(dictLength(0) > 0, "dictionary length should be positive after setDictionary + deflate")

    // The retrieved dictionary should contain our original dictionary data
    val retrieved    = new Array[Byte](dictLength(0))
    System.arraycopy(retrievedDict, 0, retrieved, 0, dictLength(0))
    val retrievedStr = new String(retrieved)
    assert(
      retrievedStr.contains("repeated pattern"),
      s"retrieved dictionary should contain original dict content, got: $retrievedStr",
    )

    deflater.end()
  }

  test("getDictionary on uninitialized deflater returns Z_STREAM_ERROR") {
    val deflater   = new Deflater
    val dictionary = new Array[Byte](32768)
    val dictLength = new Array[Int](1)
    val err        = deflater.getDictionary(dictionary, dictLength)
    assertEquals(err, Z_STREAM_ERROR)
  }

  test("getDictionary with null dictionary returns only length") {
    val data  = "hello world".getBytes
    val compr = new Array[Byte](40000)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)
    err = deflater.deflate(Z_SYNC_FLUSH)
    assertEquals(err, Z_OK)

    val dictLength = new Array[Int](1)
    err = deflater.getDictionary(null, dictLength)
    assertEquals(err, Z_OK)
    assert(dictLength(0) > 0, "dictionary length should be positive")

    deflater.end()
  }

  test("getDictionary with null dictLength does not fail") {
    val data  = "hello world".getBytes
    val compr = new Array[Byte](40000)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)
    err = deflater.deflate(Z_SYNC_FLUSH)
    assertEquals(err, Z_OK)

    val dictionary = new Array[Byte](32768)
    err = deflater.getDictionary(dictionary, null)
    assertEquals(err, Z_OK)

    deflater.end()
  }

  test("getDictionary after end returns Z_STREAM_ERROR") {
    val deflater = new Deflater
    deflater.init(Z_DEFAULT_COMPRESSION)
    deflater.end()

    val dictionary = new Array[Byte](32768)
    val dictLength = new Array[Int](1)
    val err        = deflater.getDictionary(dictionary, dictLength)
    assertEquals(err, Z_STREAM_ERROR)
  }
}
