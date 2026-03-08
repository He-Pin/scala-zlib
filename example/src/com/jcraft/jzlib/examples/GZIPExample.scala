package com.jcraft.jzlib.examples

import com.jcraft.jzlib._
import java.io._

/**
 * Demonstrates GZIP compression and decompression using GZIPOutputStream and GZIPInputStream.
 *
 * This example shows:
 *   - Compressing data with GZIPOutputStream including GZIP metadata
 *   - Decompressing data with GZIPInputStream and reading metadata
 *   - Full round-trip verification
 *
 * Note: GZIP streams are JVM-only (they extend java.io.Filter*Stream).
 *
 * Run with: ./mill example.runMain com.jcraft.jzlib.examples.GZIPExample
 */
object GZIPExample {

  def main(args: Array[String]): Unit = {
    val message  =
      "GZIP is the most common compression format for files and HTTP. " * 50
    val original = message.getBytes("UTF-8")
    println(s"Original size: ${original.length} bytes")

    // ── Compress with GZIP ──────────────────────────────────────────────
    val compressed = gzipCompress(original)
    println(s"GZIP compressed size: ${compressed.length} bytes")
    println(
      f"Compression ratio: ${compressed.length.toDouble / original.length * 100}%.1f%%",
    )

    // ── Decompress with GZIP ────────────────────────────────────────────
    val decompressed = gzipDecompress(compressed)
    println(s"Decompressed size: ${decompressed.length} bytes")
    println(
      s"Round-trip OK: ${java.util.Arrays.equals(original, decompressed)}",
    )

    // ── Compress with metadata ──────────────────────────────────────────
    println("\n--- GZIP with metadata ---")
    compressWithMetadata(original)
  }

  /** Compress data to GZIP format. */
  def gzipCompress(data: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    gos.close()
    baos.toByteArray
  }

  /** Decompress GZIP data. */
  def gzipDecompress(data: Array[Byte]): Array[Byte] = {
    val bais = new ByteArrayInputStream(data)
    val gis  = new GZIPInputStream(bais)

    // Read all decompressed data
    val baos = new ByteArrayOutputStream()
    val buf  = new Array[Byte](4096)
    var n    = gis.read(buf)
    while (n != -1) {
      baos.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()
    baos.toByteArray
  }

  /**
   * Shows how to set and read GZIP metadata (filename, comment, modification time).
   */
  def compressWithMetadata(data: Array[Byte]): Unit = {
    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)

    // Set GZIP header metadata before writing data
    gos.setName("example.txt")
    gos.setComment("Compressed by scala-zlib example")
    gos.setModifiedTime(System.currentTimeMillis() / 1000)
    gos.setOS(0xff) // unknown OS

    gos.write(data)
    gos.close()

    val compressed = baos.toByteArray
    println(s"Compressed with metadata: ${compressed.length} bytes")
    println(s"CRC-32 of original: 0x${gos.getCRC().toHexString.toUpperCase}")

    // Now read it back and inspect metadata
    val bais = new ByteArrayInputStream(compressed)
    val gis  = new GZIPInputStream(bais)

    // Read all data to process the stream
    val readBuf = new Array[Byte](4096)
    val readOut = new ByteArrayOutputStream()
    var n       = gis.read(readBuf)
    while (n != -1) {
      readOut.write(readBuf, 0, n)
      n = gis.read(readBuf)
    }
    gis.close()

    println(s"Name: ${gis.getName()}")
    println(s"Comment: ${gis.getComment()}")
    println(s"Modified time: ${gis.getModifiedTime()}")
    println(s"CRC-32 on read: 0x${gis.getCRC().toHexString.toUpperCase}")
  }
}
