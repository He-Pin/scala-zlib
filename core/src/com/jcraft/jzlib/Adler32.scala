/* -*-mode:scala; c-basic-offset:2; -*- */
/*
Copyright (c) 2000-2011 ymnk, JCraft,Inc. All rights reserved.

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
 * Adler-32 checksum as defined in RFC 1950, used by the ZLIB wrapper format.
 *
 * Produces the same results as `java.util.zip.Adler32` but works on all Scala platforms (JVM, Scala.js, Scala Native,
 * WASM).
 *
 * Instances are '''not''' thread-safe; each thread should use its own instance.
 *
 * To merge checksums computed over separate data segments, use [[Adler32$.combine `Adler32.combine`]].
 */
final class Adler32 extends Checksum {

  // largest prime smaller than 65536
  private final val BASE = 65521
  // NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
  private final val NMAX = 5552

  private var s1: Long = 1L
  private var s2: Long = 0L

  def reset(init: Long): Unit = {
    s1 = init & 0xffffL
    s2 = (init >> 16) & 0xffffL
  }

  def reset(): Unit = {
    s1 = 1L
    s2 = 0L
  }

  def getValue: Long = (s2 << 16) | s1

  def update(buf: Array[Byte], index: Int, len: Int): Unit = {
    var i   = index
    var rem = len
    if (rem == 1) {
      s1 += buf(i) & 0xff; s2 += s1
      s1 %= BASE
      s2 %= BASE
      return
    }

    var len1 = rem / NMAX
    val len2 = rem % NMAX
    while (len1 > 0) {
      len1 -= 1
      var k = NMAX
      rem -= k
      while (k > 0) {
        k -= 1
        s1 += buf(i) & 0xff; i += 1; s2 += s1
      }
      s1 %= BASE
      s2 %= BASE
    }

    var k = len2
    rem -= k
    while (k > 0) {
      k -= 1
      s1 += buf(i) & 0xff; i += 1; s2 += s1
    }
    s1 %= BASE
    s2 %= BASE
  }

  def copy(): Adler32 = {
    val foo = new Adler32
    foo.s1 = s1
    foo.s2 = s2
    foo
  }
}

/** Companion providing the [[combine]] utility for merging Adler-32 checksums. */
object Adler32 {

  /**
   * Combines two Adler-32 checksums into the checksum of the concatenated data.
   *
   * Given `adler1 = adler32(data1)` and `adler2 = adler32(data2)`, returns `adler32(data1 ++ data2)` without access to
   * the original data.
   *
   * @param adler1
   *   checksum of the first segment
   * @param adler2
   *   checksum of the second segment
   * @param len2
   *   byte length of the second segment
   * @return
   *   combined Adler-32 checksum
   */
  // The following logic has come from zlib.1.2.
  def combine(adler1: Long, adler2: Long, len2: Long): Long = {
    val BASEL      = 65521L
    var sum1: Long = 0L
    var sum2: Long = 0L

    val rem = len2 % BASEL
    sum1 = adler1 & 0xffffL
    sum2 = rem * sum1
    sum2 %= BASEL
    sum1 += (adler2 & 0xffffL) + BASEL - 1
    sum2 += ((adler1 >> 16) & 0xffffL) + ((adler2 >> 16) & 0xffffL) + BASEL - rem
    if (sum1 >= BASEL) sum1 -= BASEL
    if (sum1 >= BASEL) sum1 -= BASEL
    if (sum2 >= (BASEL << 1)) sum2 -= (BASEL << 1)
    if (sum2 >= BASEL) sum2 -= BASEL
    sum1 | (sum2 << 16)
  }
}
