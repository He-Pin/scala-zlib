package com.jcraft.jzlib.examples

import com.jcraft.jzlib._

/**
 * Demonstrates basic deflate/inflate using Deflater and Inflater.
 *
 * This example shows:
 *   - Compressing data with Deflater
 *   - Decompressing data with Inflater
 *   - A full round-trip verifying data integrity
 *
 * Run with: ./mill example.runMain com.jcraft.jzlib.examples.BasicCompression
 */
object BasicCompression {

  def main(args: Array[String]): Unit = {
    val message  =
      "Hello, scala-zlib! This is a compression test. " * 20
    val original = message.getBytes("UTF-8")
    println(s"Original size: ${original.length} bytes")

    // ── Compress ────────────────────────────────────────────────────────
    val compressed = compress(original)
    println(s"Compressed size: ${compressed.length} bytes")
    println(
      f"Compression ratio: ${compressed.length.toDouble / original.length * 100}%.1f%%",
    )

    // ── Decompress ──────────────────────────────────────────────────────
    val decompressed = decompress(compressed, original.length)
    println(s"Decompressed size: ${decompressed.length} bytes")
    println(
      s"Round-trip OK: ${java.util.Arrays.equals(original, decompressed)}",
    )

    // ── Try different compression levels ────────────────────────────────
    println("\n--- Compression levels ---")
    for (
      level <- Seq(
                 JZlib.Z_NO_COMPRESSION,
                 JZlib.Z_BEST_SPEED,
                 JZlib.Z_DEFAULT_COMPRESSION,
                 JZlib.Z_BEST_COMPRESSION,
               )
    ) {
      val name = level match {
        case JZlib.Z_NO_COMPRESSION      => "Z_NO_COMPRESSION"
        case JZlib.Z_BEST_SPEED          => "Z_BEST_SPEED"
        case JZlib.Z_DEFAULT_COMPRESSION => "Z_DEFAULT_COMPRESSION"
        case JZlib.Z_BEST_COMPRESSION    => "Z_BEST_COMPRESSION"
        case _                           => s"level $level"
      }
      val out  = compressWithLevel(original, level)
      println(f"  $name%-25s -> ${out.length}%5d bytes")
    }
  }

  /** Compress data using default compression level. */
  def compress(data: Array[Byte]): Array[Byte] = {
    compressWithLevel(data, JZlib.Z_DEFAULT_COMPRESSION)
  }

  /** Compress data at the specified compression level. */
  def compressWithLevel(data: Array[Byte], level: Int): Array[Byte] = {
    val deflater = new Deflater()
    deflater.init(level)

    // Set input data
    deflater.setInput(data)

    // Allocate output buffer (deflateBound gives an upper bound)
    val maxLen = JZlib.deflateBound(data.length.toLong).toInt
    val output = new Array[Byte](maxLen)
    deflater.setOutput(output)

    // Deflate until done
    var done = false
    while (!done) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err != JZlib.Z_OK)
        throw new RuntimeException(
          s"Deflate error: ${JZlib.getErrorDescription(err)}",
        )
    }

    val compressedLen = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, compressedLen)
  }

  /** Decompress data that was compressed with zlib wrapper (default). */
  def decompress(data: Array[Byte], expectedLen: Int): Array[Byte] = {
    val inflater = new Inflater()
    inflater.init()

    // Set compressed input
    inflater.setInput(data)

    // Allocate output buffer
    val output = new Array[Byte](expectedLen)
    inflater.setOutput(output)

    // Inflate until done
    var done = false
    while (!done) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err != JZlib.Z_OK)
        throw new RuntimeException(
          s"Inflate error: ${JZlib.getErrorDescription(err)}",
        )
    }

    inflater.end()
    output
  }
}
