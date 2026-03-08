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

private[jzlib] object InfBlocks {
  private final val MANY: Int = 1440

  private final val inflate_mask: Array[Int] = Array(
    0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
    0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
    0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
    0x00007fff, 0x0000ffff,
  )

  private[jzlib] final val border: Array[Int] = Array(
    16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
  )

  private final val Z_OK            = 0
  private final val Z_STREAM_END    = 1
  private final val Z_NEED_DICT     = 2
  private final val Z_ERRNO         = -1
  private final val Z_STREAM_ERROR  = -2
  private final val Z_DATA_ERROR    = -3
  private final val Z_MEM_ERROR     = -4
  private final val Z_BUF_ERROR     = -5
  private final val Z_VERSION_ERROR = -6

  private final val TYPE   = 0
  private final val LENS   = 1
  private final val STORED = 2
  private final val TABLE  = 3
  private final val BTREE  = 4
  private final val DTREE  = 5
  private final val CODES  = 6
  private final val DRY    = 7
  private final val DONE   = 8
  private final val BAD    = 9
}

private[jzlib] final class InfBlocks(private val z: ZStream, w: Int) {
  import InfBlocks._

  private[jzlib] var mode: Int             = 0
  private[jzlib] var left: Int             = 0
  private[jzlib] var table: Int            = 0
  private[jzlib] var index: Int            = 0
  private[jzlib] var blens: Array[Int]     = null
  private[jzlib] var bb: Array[Int]        = new Array[Int](1)
  private[jzlib] var tb: Array[Int]        = new Array[Int](1)
  private[jzlib] var bl: Array[Int]        = new Array[Int](1)
  private[jzlib] var bd: Array[Int]        = new Array[Int](1)
  private[jzlib] var tl: Array[Array[Int]] = new Array[Array[Int]](1)
  private[jzlib] var td: Array[Array[Int]] = new Array[Array[Int]](1)
  private[jzlib] var tli: Array[Int]       = new Array[Int](1)
  private[jzlib] var tdi: Array[Int]       = new Array[Int](1)
  private val codes: InfCodes              = new InfCodes(z, this)
  private[jzlib] var last: Int             = 0
  private[jzlib] var bitk: Int             = 0
  private[jzlib] var bitb: Int             = 0
  private[jzlib] var hufts: Array[Int]     = new Array[Int](MANY * 3)
  private[jzlib] var window: Array[Byte]   = new Array[Byte](w)
  private[jzlib] var end: Int              = w
  private[jzlib] var read: Int             = 0
  private[jzlib] var write: Int            = 0
  private var check: Boolean               = z.istate.wrap != 0
  private val inftree: InfTree             = new InfTree()

  mode = TYPE
  reset()

  private[jzlib] def reset(): Unit = {
    if (mode == BTREE || mode == DTREE) {}
    if (mode == CODES) { codes.free(z) }
    mode = TYPE
    bitk = 0
    bitb = 0
    read = 0
    write = 0
    if (check) { z.adler.reset() }
  }

  private[jzlib] def proc(r0: Int): Int = {
    var r      = r0
    var t: Int = 0
    var b: Int = 0
    var k: Int = 0
    var p: Int = 0
    var n: Int = 0
    var q: Int = 0
    var m: Int = 0
    p = z.next_in_index; n = z.avail_in; b = bitb; k = bitk
    q = write; m = if (q < read) read - q - 1 else end - q
    while (true) {
      mode match {
        case TYPE =>
          while (k < 3) {
            if (n != 0) { r = Z_OK }
            else {
              bitb = b; bitk = k
              z.avail_in = n
              z.total_in += p - z.next_in_index; z.next_in_index = p
              write = q
              return inflate_flush(r)
            }
            n -= 1
            b |= (z.next_in(p) & 0xff) << k
            p += 1
            k += 8
          }
          t = b & 7
          last = t & 1
          (t >>> 1) match {
            case 0 =>
              b >>>= 3; k -= 3
              t = k & 7
              b >>>= t; k -= t
              mode = LENS
            case 1 =>
              InfTree.inflate_trees_fixed(bl, bd, tl, td, z)
              codes.init(bl(0), bd(0), tl(0), 0, td(0), 0)
              b >>>= 3; k -= 3
              mode = CODES
            case 2 =>
              b >>>= 3; k -= 3
              mode = TABLE
            case 3 =>
              b >>>= 3; k -= 3
              mode = BAD
              z.msg = "invalid block type"
              r = Z_DATA_ERROR
              bitb = b; bitk = k
              z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
              write = q
              return inflate_flush(r)
            case _ =>
          }

        case LENS =>
          while (k < 32) {
            if (n != 0) { r = Z_OK }
            else {
              bitb = b; bitk = k
              z.avail_in = n
              z.total_in += p - z.next_in_index; z.next_in_index = p
              write = q
              return inflate_flush(r)
            }
            n -= 1
            b |= (z.next_in(p) & 0xff) << k
            p += 1
            k += 8
          }
          if ((((~b) >>> 16) & 0xffff) != (b & 0xffff)) {
            mode = BAD
            z.msg = "invalid stored block lengths"
            r = Z_DATA_ERROR
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          left = b & 0xffff
          b = 0; k = 0
          mode = if (left != 0) STORED else if (last != 0) DRY else TYPE

        case STORED =>
          if (n == 0) {
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          if (m == 0) {
            if (q == end && read != 0) { q = 0; m = if (q < read) read - q - 1 else end - q }
            if (m == 0) {
              write = q
              r = inflate_flush(r)
              q = write; m = if (q < read) read - q - 1 else end - q
              if (q == end && read != 0) { q = 0; m = if (q < read) read - q - 1 else end - q }
              if (m == 0) {
                bitb = b; bitk = k
                z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
                write = q
                return inflate_flush(r)
              }
            }
          }
          r = Z_OK
          t = left
          if (t > n) t = n
          if (t > m) t = m
          System.arraycopy(z.next_in, p, window, q, t)
          p += t; n -= t
          q += t; m -= t
          left -= t
          if (left == 0) {
            mode = if (last != 0) DRY else TYPE
          }

        case TABLE =>
          while (k < 14) {
            if (n != 0) { r = Z_OK }
            else {
              bitb = b; bitk = k
              z.avail_in = n
              z.total_in += p - z.next_in_index; z.next_in_index = p
              write = q
              return inflate_flush(r)
            }
            n -= 1
            b |= (z.next_in(p) & 0xff) << k
            p += 1
            k += 8
          }
          table = b & 0x3fff
          t = table
          if ((t & 0x1f) > 29 || ((t >> 5) & 0x1f) > 29) {
            mode = BAD
            z.msg = "too many length or distance symbols"
            r = Z_DATA_ERROR
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          t = 258 + (t & 0x1f) + ((t >> 5) & 0x1f)
          if (blens == null || blens.length < t) { blens = new Array[Int](t) }
          else { var i = 0; while (i < t) { blens(i) = 0; i += 1 } }
          b >>>= 14; k -= 14
          index = 0
          mode = BTREE

        case BTREE =>
          while (index < 4 + (table >>> 10)) {
            while (k < 3) {
              if (n != 0) { r = Z_OK }
              else {
                bitb = b; bitk = k
                z.avail_in = n
                z.total_in += p - z.next_in_index; z.next_in_index = p
                write = q
                return inflate_flush(r)
              }
              n -= 1
              b |= (z.next_in(p) & 0xff) << k
              p += 1
              k += 8
            }
            blens(border(index)) = b & 7
            index += 1
            b >>>= 3; k -= 3
          }
          while (index < 19) { blens(border(index)) = 0; index += 1 }
          bb(0) = 7
          t = inftree.inflate_trees_bits(blens, bb, tb, hufts, z)
          if (t != Z_OK) {
            r = t
            if (r == Z_DATA_ERROR) { blens = null; mode = BAD }
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          index = 0
          mode = DTREE

        case DTREE =>
          var dtreeLoop = true
          while (dtreeLoop) {
            t = table
            if (!(index < 258 + (t & 0x1f) + ((t >> 5) & 0x1f))) {
              dtreeLoop = false
            } else {
              var i: Int = 0
              var j: Int = 0
              var c: Int = 0
              t = bb(0)
              while (k < t) {
                if (n != 0) { r = Z_OK }
                else {
                  bitb = b; bitk = k
                  z.avail_in = n
                  z.total_in += p - z.next_in_index; z.next_in_index = p
                  write = q
                  return inflate_flush(r)
                }
                n -= 1
                b |= (z.next_in(p) & 0xff) << k
                p += 1
                k += 8
              }
              if (tb(0) == -1) {}
              t = hufts((tb(0) + (b & inflate_mask(t))) * 3 + 1)
              c = hufts((tb(0) + (b & inflate_mask(t))) * 3 + 2)
              if (c < 16) {
                b >>>= t; k -= t
                blens(index) = c
                index += 1
              } else {
                i = if (c == 18) 7 else c - 14
                j = if (c == 18) 11 else 3
                while (k < t + i) {
                  if (n != 0) { r = Z_OK }
                  else {
                    bitb = b; bitk = k
                    z.avail_in = n
                    z.total_in += p - z.next_in_index; z.next_in_index = p
                    write = q
                    return inflate_flush(r)
                  }
                  n -= 1
                  b |= (z.next_in(p) & 0xff) << k
                  p += 1
                  k += 8
                }
                b >>>= t; k -= t
                j += b & inflate_mask(i)
                b >>>= i; k -= i
                i = index
                t = table
                if (i + j > 258 + (t & 0x1f) + ((t >> 5) & 0x1f) || (c == 16 && i < 1)) {
                  blens = null
                  mode = BAD
                  z.msg = "invalid bit length repeat"
                  r = Z_DATA_ERROR
                  bitb = b; bitk = k
                  z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
                  write = q
                  return inflate_flush(r)
                }
                c = if (c == 16) blens(i - 1) else 0
                while ({ blens(i) = c; i += 1; j -= 1; j != 0 }) ()
                index = i
              }
            }
          }
          tb(0) = -1
          bl(0) = 9
          bd(0) = 6
          t = table
          t = inftree.inflate_trees_dynamic(
            257 + (t & 0x1f),
            1 + ((t >> 5) & 0x1f),
            blens,
            bl,
            bd,
            tli,
            tdi,
            hufts,
            z,
          )
          if (t != Z_OK) {
            if (t == Z_DATA_ERROR) { blens = null; mode = BAD }
            r = t
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          codes.init(bl(0), bd(0), hufts, tli(0), hufts, tdi(0))
          mode = CODES

        case CODES =>
          bitb = b; bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          write = q
          r = codes.proc(r)
          if (r != Z_STREAM_END) {
            return inflate_flush(r)
          }
          r = Z_OK
          codes.free(z)
          p = z.next_in_index; n = z.avail_in; b = bitb; k = bitk
          q = write; m = if (q < read) read - q - 1 else end - q
          if (last == 0) {
            mode = TYPE
          } else {
            mode = DRY
          }

        case DRY =>
          write = q
          r = inflate_flush(r)
          q = write; m = if (q < read) read - q - 1 else end - q
          if (read != write) {
            bitb = b; bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            write = q
            return inflate_flush(r)
          }
          mode = DONE

        case DONE =>
          r = Z_STREAM_END
          bitb = b; bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          write = q
          return inflate_flush(r)

        case BAD =>
          r = Z_DATA_ERROR
          bitb = b; bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          write = q
          return inflate_flush(r)

        case _ =>
          r = Z_STREAM_ERROR
          bitb = b; bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          write = q
          return inflate_flush(r)
      }
    }
    r // unreachable; satisfies type checker
  }

  private[jzlib] def free(): Unit = {
    reset()
    window = null
    hufts = null
  }

  private[jzlib] def set_dictionary(d: Array[Byte], start: Int, n: Int): Unit = {
    System.arraycopy(d, start, window, 0, n)
    read = n
    write = n
  }

  private[jzlib] def sync_point(): Int = {
    if (mode == LENS) 1 else 0
  }

  private[jzlib] def inflate_flush(r0: Int): Int = {
    var r      = r0
    var n: Int = 0
    var p: Int = 0
    var q: Int = 0
    p = z.next_out_index
    q = read
    n = (if (q <= write) write else end) - q
    if (n > z.avail_out) n = z.avail_out
    if (n != 0 && r == Z_BUF_ERROR) r = Z_OK
    z.avail_out -= n
    z.total_out += n
    if (check && n > 0) { z.adler.update(window, q, n) }
    System.arraycopy(window, q, z.next_out, p, n)
    p += n
    q += n
    if (q == end) {
      q = 0
      if (write == end) write = 0
      n = write - q
      if (n > z.avail_out) n = z.avail_out
      if (n != 0 && r == Z_BUF_ERROR) r = Z_OK
      z.avail_out -= n
      z.total_out += n
      if (check && n > 0) { z.adler.update(window, q, n) }
      System.arraycopy(window, q, z.next_out, p, n)
      p += n
      q += n
    }
    z.next_out_index = p
    read = q
    r
  }
}
