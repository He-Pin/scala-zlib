package com.jcraft.jzlib.examples

import com.jcraft.jzlib._

/**
 * Demonstrates Adler-32 and CRC-32 checksum usage.
 *
 * This example shows:
 *   - Computing Adler-32 checksums (used in zlib format)
 *   - Computing CRC-32 checksums (used in GZIP format)
 *   - Combining checksums from separate data segments
 *   - Copying checksum state for incremental computation
 *
 * Run with: ./mill example.runMain com.jcraft.jzlib.examples.ChecksumExample
 */
object ChecksumExample {

  def main(args: Array[String]): Unit = {
    adler32Demo()
    println()
    crc32Demo()
    println()
    combineDemo()
  }

  /** Basic Adler-32 checksum computation. */
  def adler32Demo(): Unit = {
    println("--- Adler-32 ---")
    val data = "The quick brown fox jumps over the lazy dog".getBytes("UTF-8")

    val adler = new Adler32()
    adler.update(data, 0, data.length)
    println(f"Adler-32 of '${new String(data)}': 0x${adler.getValue}%08X")

    // Incremental update: compute checksum one byte at a time
    val adler2 = new Adler32()
    for (b <- data) {
      adler2.update(Array(b), 0, 1)
    }
    println(f"Adler-32 (incremental): 0x${adler2.getValue}%08X")
    println(s"Values match: ${adler.getValue == adler2.getValue}")

    // Copy and continue
    val adlerCopy = adler.copy()
    val moreData  = " — and more data!".getBytes("UTF-8")
    adlerCopy.update(moreData, 0, moreData.length)
    println(f"Adler-32 after appending: 0x${adlerCopy.getValue}%08X")
  }

  /** Basic CRC-32 checksum computation. */
  def crc32Demo(): Unit = {
    println("--- CRC-32 ---")
    val data = "The quick brown fox jumps over the lazy dog".getBytes("UTF-8")

    val crc = new CRC32()
    crc.update(data, 0, data.length)
    println(f"CRC-32 of '${new String(data)}': 0x${crc.getValue}%08X")

    // Incremental update
    val crc2 = new CRC32()
    for (b <- data) {
      crc2.update(Array(b), 0, 1)
    }
    println(f"CRC-32 (incremental): 0x${crc2.getValue}%08X")
    println(s"Values match: ${crc.getValue == crc2.getValue}")

    // Copy and continue
    val crcCopy  = crc.copy()
    val moreData = " — and more data!".getBytes("UTF-8")
    crcCopy.update(moreData, 0, moreData.length)
    println(f"CRC-32 after appending: 0x${crcCopy.getValue}%08X")
  }

  /**
   * Demonstrates combining checksums from separate data segments.
   *
   * This is useful when data is processed in parallel or in chunks: compute each chunk's checksum independently, then
   * combine them.
   */
  def combineDemo(): Unit = {
    println("--- Combine checksums ---")
    val part1 = "Hello, ".getBytes("UTF-8")
    val part2 = "world!".getBytes("UTF-8")

    // Compute checksums for each part
    val adler1 = { val a = new Adler32(); a.update(part1, 0, part1.length); a.getValue }
    val adler2 = { val a = new Adler32(); a.update(part2, 0, part2.length); a.getValue }

    // Combine them
    val combined = Adler32.combine(adler1, adler2, part2.length.toLong)

    // Verify against computing over the whole data
    val whole    = part1 ++ part2
    val expected = { val a = new Adler32(); a.update(whole, 0, whole.length); a.getValue }

    println(f"Adler-32 part1: 0x${adler1}%08X")
    println(f"Adler-32 part2: 0x${adler2}%08X")
    println(f"Combined:       0x${combined}%08X")
    println(f"Expected:       0x${expected}%08X")
    println(s"Combine OK: ${combined == expected}")

    // Same for CRC-32
    val crc1        = { val c = new CRC32(); c.update(part1, 0, part1.length); c.getValue }
    val crc2        = { val c = new CRC32(); c.update(part2, 0, part2.length); c.getValue }
    val crcCombined = CRC32.combine(crc1, crc2, part2.length.toLong)
    val crcExpected = { val c = new CRC32(); c.update(whole, 0, whole.length); c.getValue }

    println(f"\nCRC-32 part1:   0x${crc1}%08X")
    println(f"CRC-32 part2:   0x${crc2}%08X")
    println(f"Combined:       0x${crcCombined}%08X")
    println(f"Expected:       0x${crcExpected}%08X")
    println(s"Combine OK: ${crcCombined == crcExpected}")
  }
}
