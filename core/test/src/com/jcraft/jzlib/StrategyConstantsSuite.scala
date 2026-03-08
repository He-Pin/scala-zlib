package com.jcraft.jzlib

class StrategyConstantsSuite extends munit.FunSuite {
  test("Z_RLE and Z_FIXED strategy constants exist and work") {
    val d = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION)

    val data = Array.fill[Byte](1000)(42)
    d.setInput(data)
    d.setNextInIndex(0)
    d.setAvailIn(data.length)

    val output = new Array[Byte](2000)
    d.setOutput(output)
    d.setNextOutIndex(0)
    d.setAvailOut(output.length)

    // Test Z_RLE strategy
    d.params(JZlib.Z_DEFAULT_COMPRESSION, JZlib.Z_RLE)

    // Test Z_FIXED strategy
    d.params(JZlib.Z_DEFAULT_COMPRESSION, JZlib.Z_FIXED)

    d.end()
  }

  test("strategy constants have expected values") {
    assertEquals(JZlib.Z_FILTERED, 1)
    assertEquals(JZlib.Z_HUFFMAN_ONLY, 2)
    assertEquals(JZlib.Z_RLE, 3)
    assertEquals(JZlib.Z_FIXED, 4)
    assertEquals(JZlib.Z_DEFAULT_STRATEGY, 0)
  }
}
