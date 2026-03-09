/* -*-mode:scala; c-basic-offset:2; -*- */
/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

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

object Tree {
  private final val MAX_BITS     = 15
  private final val BL_CODES     = 19
  private final val D_CODES      = 30
  private final val LITERALS     = 256
  private final val LENGTH_CODES = 29
  private final val L_CODES      = LITERALS + 1 + LENGTH_CODES
  private final val HEAP_SIZE    = 2 * L_CODES + 1

  final val MAX_BL_BITS = 7
  final val END_BLOCK   = 256
  final val REP_3_6     = 16
  final val REPZ_3_10   = 17
  final val REPZ_11_138 = 18

  final val extra_lbits: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
  )

  final val extra_dbits: Array[Int] = Array(
    0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
  )

  final val extra_blbits: Array[Int] = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7,
  )

  final val bl_order: Array[Byte] = Array(
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
  )

  final val Buf_size = 8 * 2

  final val DIST_CODE_LEN = 512

  final val _dist_code: Array[Byte] = Array(
    0, 1, 2, 3, 4, 4, 5, 5, 6, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8, 8,
    8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10,
    10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
    11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13,
    13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13,
    13, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
    14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
    14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
    14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15,
    15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
    15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
    15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 0, 0, 16, 17,
    18, 18, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 22,
    23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
    26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26,
    26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27,
    27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27,
    27, 27, 27, 27, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
    28, 28, 28, 28, 28, 28, 28, 28, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
    29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
    29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
    29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
  )

  final val _length_code: Array[Byte] = Array(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 12, 12,
    13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16, 16, 16, 16, 16,
    17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 19,
    19, 19, 19, 19, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20,
    21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23,
    23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
    25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 26, 26,
    26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26,
    26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27,
    27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 28,
  )

  final val base_length: Array[Int] = Array(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56,
    64, 80, 96, 112, 128, 160, 192, 224, 0,
  )

  final val base_dist: Array[Int] = Array(
    0, 1, 2, 3, 4, 6, 8, 12, 16, 24,
    32, 48, 64, 96, 128, 192, 256, 384, 512, 768,
    1024, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576,
  )

  def d_code(dist: Int): Int =
    if (dist < 256) _dist_code(dist) & 0xff else _dist_code(256 + (dist >>> 7)) & 0xff

  private def gen_codes(
    tree: Array[Short],
    max_code: Int,
    bl_count: Array[Short],
    next_code: Array[Short],
  ): Unit = {
    var code: Short = 0
    var bits        = 0
    var n           = 0

    next_code(0) = 0
    bits = 1
    while (bits <= MAX_BITS) {
      code = ((code + bl_count(bits - 1)) << 1).toShort
      next_code(bits) = code
      bits += 1
    }

    n = 0
    while (n <= max_code) {
      val len = tree(n * 2 + 1)
      if (len != 0) {
        tree(n * 2) = bi_reverse(next_code(len), len).toShort
        next_code(len) = (next_code(len) + 1).toShort
      }
      n += 1
    }
  }

  private def bi_reverse(code: Int, len: Int): Int = {
    var res = 0
    var c   = code
    var l   = len;
    {
      var continueLoop_7282 = true;
      while (continueLoop_7282) {
        res |= c & 1
        c >>>= 1
        res <<= 1
        l -= 1
        continueLoop_7282 = l > 0
      }
    }
    res >>> 1
  }
}

final class Tree {
  import Tree._

  var dyn_tree: Array[Short] = null
  var max_code: Int          = 0
  var stat_desc: StaticTree  = null

  def gen_bitlen(s: Deflate): Unit = {
    val tree       = dyn_tree
    val stree      = stat_desc.static_tree
    val extra      = stat_desc.extra_bits
    val base       = stat_desc.extra_base
    val max_length = stat_desc.max_length
    var h          = 0
    var n          = 0
    var m          = 0
    var bits       = 0
    var xbits      = 0
    var f: Short   = 0
    var overflow   = 0

    bits = 0
    while (bits <= MAX_BITS) { s.bl_count(bits) = 0; bits += 1 }

    tree(s.heap(s.heap_max) * 2 + 1) = 0

    h = s.heap_max + 1
    while (h < HEAP_SIZE) {
      n = s.heap(h)
      bits = tree(tree(n * 2 + 1) * 2 + 1) + 1
      if (bits > max_length) { bits = max_length; overflow += 1 }
      tree(n * 2 + 1) = bits.toShort

      if (n > max_code) { h += 1 }
      else {
        s.bl_count(bits) = (s.bl_count(bits) + 1).toShort
        xbits = 0
        if (n >= base) xbits = extra(n - base)
        f = tree(n * 2)
        s.opt_len += f * (bits + xbits)
        if (stree != null) s.static_len += f * (stree(n * 2 + 1) + xbits)
        h += 1
      }
    }
    if (overflow == 0) return;
    {
      var continueLoop_8708 = true;
      while (continueLoop_8708) {
        bits = max_length - 1
        while (s.bl_count(bits) == 0) bits -= 1
        s.bl_count(bits) = (s.bl_count(bits) - 1).toShort
        s.bl_count(bits + 1) = (s.bl_count(bits + 1) + 2).toShort
        s.bl_count(max_length) = (s.bl_count(max_length) - 1).toShort
        overflow -= 2
        continueLoop_8708 = overflow > 0
      }
    }

    bits = max_length
    while (bits != 0) {
      n = s.bl_count(bits)
      while (n != 0) {
        m = s.heap { h -= 1; h }
        if (m > max_code) { n -= 1 }
        else {
          if (tree(m * 2 + 1) != bits) {
            s.opt_len += ((bits.toLong - tree(m * 2 + 1).toLong) * tree(m * 2).toLong).toInt
            tree(m * 2 + 1) = bits.toShort
          }
          n -= 1
        }
      }
      bits -= 1
    }
  }

  def build_tree(s: Deflate): Unit = {
    val tree     = dyn_tree
    val stree    = stat_desc.static_tree
    val elems    = stat_desc.elems
    var n        = 0
    var m        = 0
    var max_code = -1
    var node     = 0

    s.heap_len = 0
    s.heap_max = HEAP_SIZE

    n = 0
    while (n < elems) {
      if (tree(n * 2) != 0) {
        s.heap_len += 1
        s.heap(s.heap_len) = n
        max_code = n
        s.depth(n) = 0
      } else {
        tree(n * 2 + 1) = 0
      }
      n += 1
    }

    while (s.heap_len < 2) {
      node = {
        s.heap_len += 1;
        if (max_code < 2) { max_code += 1; max_code }
        else 0
      }
      s.heap(s.heap_len) = node
      tree(node * 2) = 1
      s.depth(node) = 0
      s.opt_len -= 1
      if (stree != null) s.static_len -= stree(node * 2 + 1)
    }
    this.max_code = max_code

    n = s.heap_len / 2
    while (n >= 1) { s.pqdownheap(tree, n); n -= 1 }

    node = elems;
    {
      var continueLoop_10415 = true;
      while (continueLoop_10415) {
        n = s.heap(1)
        s.heap(1) = s.heap(s.heap_len)
        s.heap_len -= 1
        s.pqdownheap(tree, 1)
        m = s.heap(1)

        s.heap_max -= 1; s.heap(s.heap_max) = n
        s.heap_max -= 1; s.heap(s.heap_max) = m

        tree(node * 2) = (tree(n * 2) + tree(m * 2)).toShort
        s.depth(node) = (math.max(s.depth(n), s.depth(m)) + 1).toByte
        tree(n * 2 + 1) = node.toShort
        tree(m * 2 + 1) = node.toShort

        s.heap(1) = node
        node += 1
        s.pqdownheap(tree, 1)
        continueLoop_10415 = s.heap_len >= 2
      }
    }

    s.heap_max -= 1; s.heap(s.heap_max) = s.heap(1)

    gen_bitlen(s)

    Tree.gen_codes(tree, max_code, s.bl_count, s.next_code)
  }
}
