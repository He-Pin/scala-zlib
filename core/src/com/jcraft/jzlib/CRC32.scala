/* -*-mode:scala; c-basic-offset:2; -*- */
/*
Copyright (c) 2011 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package com.jcraft.jzlib

/**
 * CRC-32 checksum as defined in RFC 1952, used by the GZIP wrapper format.
 *
 * Produces the same results as `java.util.zip.CRC32` but works on all Scala platforms (JVM, Scala.js, Scala Native,
 * WASM).
 *
 * Instances are '''not''' thread-safe; each thread should use its own instance.
 *
 * To merge checksums computed over separate data segments, use [[CRC32$.combine `CRC32.combine`]].
 */
final class CRC32 extends Checksum {

  private var v: Int = 0

  def update(buf: Array[Byte], index: Int, len: Int): Unit = {
    var c   = ~v
    var i   = index
    var rem = len
    while (rem > 0) {
      rem -= 1
      c = CRC32.crc_table((c ^ buf(i)) & 0xff) ^ (c >>> 8)
      i += 1
    }
    v = ~c
  }

  def reset(): Unit = v = 0

  def reset(vv: Long): Unit = v = (vv & 0xffffffffL).toInt

  def getValue: Long = v & 0xffffffffL

  def copy(): CRC32 = {
    val foo = new CRC32
    foo.v = v
    foo
  }
}

/** Companion providing the CRC-32 lookup table and [[combine]] utility. */
object CRC32 {
  /*
   * The following logic has come from RFC1952.
   */
  val crc_table: Array[Int] = {
    val tbl = new Array[Int](256)
    var n   = 0
    while (n < 256) {
      var c = n
      var k = 8
      while (k > 0) {
        k -= 1
        if ((c & 1) != 0) c = 0xedb88320 ^ (c >>> 1)
        else c = c >>> 1
      }
      tbl(n) = c
      n += 1
    }
    tbl
  }

  private final val GF2_DIM = 32

  /**
   * Combines two CRC-32 checksums into the checksum of the concatenated data.
   *
   * Given `crc1 = crc32(data1)` and `crc2 = crc32(data2)`, returns `crc32(data1 ++ data2)` without access to the
   * original data.
   *
   * @param crc1
   *   checksum of the first segment
   * @param crc2
   *   checksum of the second segment
   * @param len2
   *   byte length of the second segment
   * @return
   *   combined CRC-32 checksum
   */
  // The following logic has come from zlib.1.2.
  def combine(crc1: Long, crc2: Long, len2: Long): Long = {
    if (len2 <= 0) return crc1

    var c1 = crc1
    var l2 = len2

    val even = new Array[Long](GF2_DIM)
    val odd  = new Array[Long](GF2_DIM)

    // put operator for one zero bit in odd
    odd(0) = 0xedb88320L
    var row = 1L
    var n   = 1
    while (n < GF2_DIM) {
      odd(n) = row; row <<= 1; n += 1
    }

    // put operator for two zero bits in even
    gf2_matrix_square(even, odd)
    // put operator for four zero bits in odd
    gf2_matrix_square(odd, even)

    // apply len2 zeros to crc1
    var done = false
    while (!done) {
      gf2_matrix_square(even, odd)
      if ((l2 & 1) != 0) c1 = gf2_matrix_times(even, c1)
      l2 >>= 1
      if (l2 == 0) { done = true }
      else {
        gf2_matrix_square(odd, even)
        if ((l2 & 1) != 0) c1 = gf2_matrix_times(odd, c1)
        l2 >>= 1
        if (l2 == 0) done = true
      }
    }

    c1 ^ crc2
  }

  private def gf2_matrix_times(mat: Array[Long], vec: Long): Long = {
    var sum   = 0L
    var v     = vec
    var index = 0
    while (v != 0) {
      if ((v & 1) != 0) sum ^= mat(index)
      v >>= 1; index += 1
    }
    sum
  }

  def gf2_matrix_square(square: Array[Long], mat: Array[Long]): Unit = {
    var n = 0
    while (n < GF2_DIM) {
      square(n) = gf2_matrix_times(mat, mat(n)); n += 1
    }
  }

  def getCRC32Table: Array[Int] = {
    val tmp = new Array[Int](crc_table.length)
    System.arraycopy(crc_table, 0, tmp, 0, tmp.length)
    tmp
  }
}
