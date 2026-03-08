package com.jcraft.jzlib.examples

import com.jcraft.jzlib._

/**
 * Demonstrates the one-shot compress/uncompress utilities in JZlib.
 *
 * This example shows:
 *   - JZlib.compress() for one-shot compression
 *   - JZlib.uncompress() for one-shot decompression
 *   - JZlib.deflateBound() / JZlib.compressBound() for buffer sizing
 *   - JZlib.version() and JZlib.getErrorDescription()
 *
 * These utilities are the simplest way to compress and decompress data when you have the entire input available in
 * memory.
 *
 * Run with: ./mill example.runMain com.jcraft.jzlib.examples.UtilityExample
 */
object UtilityExample {

  def main(args: Array[String]): Unit = {
    // ── Library info ────────────────────────────────────────────────────
    println(s"scala-zlib version: ${JZlib.version()}")
    println()

    // ── One-shot compress/uncompress ────────────────────────────────────
    oneShot()
    println()

    // ── Buffer sizing ───────────────────────────────────────────────────
    bufferSizing()
    println()

    // ── Error descriptions ──────────────────────────────────────────────
    errorDescriptions()
  }

  /** Demonstrates JZlib.compress() and JZlib.uncompress(). */
  def oneShot(): Unit = {
    println("--- One-shot compress/uncompress ---")
    val message  = "This is the simplest way to use scala-zlib! " * 10
    val original = message.getBytes("UTF-8")
    println(s"Original: ${original.length} bytes")

    // Compress in one call
    val compressed = JZlib.compress(original)
    println(s"Compressed: ${compressed.length} bytes")
    println(
      f"Ratio: ${compressed.length.toDouble / original.length * 100}%.1f%%",
    )

    // Uncompress in one call
    val decompressed = JZlib.uncompress(compressed)
    println(s"Decompressed: ${decompressed.length} bytes")

    val ok = java.util.Arrays.equals(original, decompressed)
    println(s"Round-trip OK: $ok")
  }

  /**
   * Demonstrates deflateBound() / compressBound() for pre-allocating output buffers.
   *
   * These functions return the maximum possible size of the compressed output for a given input size. Useful when you
   * need to allocate a buffer before compressing and want to guarantee it's large enough.
   */
  def bufferSizing(): Unit = {
    println("--- Buffer sizing with deflateBound ---")
    val sizes = Seq(100, 1000, 10000, 100000)
    println(f"${"Input size"}%12s  ${"deflateBound"}%14s  ${"compressBound"}%14s")
    println("-" * 44)
    for (size <- sizes) {
      val dBound = JZlib.deflateBound(size.toLong)
      val cBound = JZlib.compressBound(size.toLong)
      println(f"$size%12d  $dBound%14d  $cBound%14d")
    }
  }

  /** Shows human-readable descriptions for all zlib return codes. */
  def errorDescriptions(): Unit = {
    println("--- Error descriptions ---")
    val codes = Seq(
      ("Z_OK", JZlib.Z_OK),
      ("Z_STREAM_END", JZlib.Z_STREAM_END),
      ("Z_NEED_DICT", JZlib.Z_NEED_DICT),
      ("Z_ERRNO", JZlib.Z_ERRNO),
      ("Z_STREAM_ERROR", JZlib.Z_STREAM_ERROR),
      ("Z_DATA_ERROR", JZlib.Z_DATA_ERROR),
      ("Z_MEM_ERROR", JZlib.Z_MEM_ERROR),
      ("Z_BUF_ERROR", JZlib.Z_BUF_ERROR),
      ("Z_VERSION_ERROR", JZlib.Z_VERSION_ERROR),
    )
    for ((name, code) <- codes) {
      println(f"  $name%-20s ($code%2d) -> ${JZlib.getErrorDescription(code)}")
    }
  }
}
