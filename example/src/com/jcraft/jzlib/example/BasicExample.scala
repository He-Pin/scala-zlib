package com.jcraft.jzlib.example

import com.jcraft.jzlib._

/**
 * Demonstrates basic deflate/inflate usage that works on all platforms (JVM, Scala.js, Scala Native, WASM).
 */
object BasicExample {

  def main(args: Array[String]): Unit = {
    val message  =
      "Hello, scala-zlib! This is a cross-platform compression library. " * 10
    val original = message.getBytes("UTF-8")
    println(s"Original size: ${original.length} bytes")

    // Compress
    val compressed = compress(original)
    println(s"Compressed size: ${compressed.length} bytes")
    println(
      f"Compression ratio: ${compressed.length.toDouble / original.length * 100}%.1f%%",
    )

    // Decompress
    val decompressed = decompress(compressed, original.length)
    println(s"Decompressed size: ${decompressed.length} bytes")
    println(
      s"Round-trip OK: ${java.util.Arrays.equals(original, decompressed)}",
    )

    // Checksums
    val adler = new Adler32()
    adler.update(original, 0, original.length)
    println(f"Adler-32: 0x${adler.getValue}%08X")

    val crc = new CRC32()
    crc.update(original, 0, original.length)
    println(f"CRC-32: 0x${crc.getValue}%08X")
  }

  def compress(data: Array[Byte]): Array[Byte] = {
    val deflater = new Deflater()
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    deflater.setInput(data)
    val output   = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)
    var done     = false
    while (!done) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err < 0) throw new RuntimeException(s"Deflate error: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, len)
  }

  def decompress(data: Array[Byte], expectedLen: Int): Array[Byte] = {
    val inflater = new Inflater()
    inflater.init()
    inflater.setInput(data)
    val output   = new Array[Byte](expectedLen)
    inflater.setOutput(output)
    var done     = false
    while (!done) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err != JZlib.Z_OK)
        throw new RuntimeException(s"Inflate error: $err")
    }
    inflater.end()
    output
  }
}
