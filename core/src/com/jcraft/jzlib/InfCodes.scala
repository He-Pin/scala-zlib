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

private[jzlib] object InfCodes {
  private final val inflate_mask: Array[Int] = Array(
    0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
    0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
    0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
    0x00007fff, 0x0000ffff,
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

  private final val START   = 0 // x: set up for LEN
  private final val LEN     = 1 // i: get length/literal/eob next
  private final val LENEXT  = 2 // i: getting length extra (have base)
  private final val DIST    = 3 // i: get distance next
  private final val DISTEXT = 4 // i: getting distance extra
  private final val COPY    = 5 // o: copying bytes in window, waiting for space
  private final val LIT     = 6 // o: got literal, waiting for output space
  private final val WASH    = 7 // o: got eob, possibly still output waiting
  private final val END     = 8 // x: got eob and all data flushed
  private final val BADCODE = 9 // x: got error
}

private[jzlib] final class InfCodes(private val z: ZStream, private val s: InfBlocks) {
  import InfCodes._

  private[jzlib] var mode: Int = 0

  private[jzlib] var len: Int = 0

  private[jzlib] var tree: Array[Int] = null
  private[jzlib] var tree_index: Int  = 0
  private[jzlib] var need: Int        = 0

  private[jzlib] var lit: Int = 0

  private[jzlib] var get: Int  = 0
  private[jzlib] var dist: Int = 0

  private[jzlib] var lbits: Byte       = 0
  private[jzlib] var dbits: Byte       = 0
  private[jzlib] var ltree: Array[Int] = null
  private[jzlib] var ltree_index: Int  = 0
  private[jzlib] var dtree: Array[Int] = null
  private[jzlib] var dtree_index: Int  = 0

  private[jzlib] def init(bl: Int, bd: Int, tl: Array[Int], tl_index: Int, td: Array[Int], td_index: Int): Unit = {
    mode = START
    lbits = bl.toByte
    dbits = bd.toByte
    ltree = tl
    ltree_index = tl_index
    dtree = td
    dtree_index = td_index
    tree = null
  }

  private[jzlib] def proc(r0: Int): Int = {
    var r           = r0
    var j: Int      = 0
    var tindex: Int = 0
    var e: Int      = 0
    var b: Int      = 0
    var k: Int      = 0
    var p: Int      = 0
    var n: Int      = 0
    var q: Int      = 0
    var m: Int      = 0
    var f: Int      = 0

    p = z.next_in_index; n = z.avail_in; b = s.bitb; k = s.bitk
    q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q

    while (true) {
      mode match {
        case START =>
          if (m >= 258 && n >= 10) {
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            r = inflate_fast(lbits, dbits, ltree, ltree_index, dtree, dtree_index, s, z)
            p = z.next_in_index; n = z.avail_in; b = s.bitb; k = s.bitk
            q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q
            if (r != Z_OK) {
              mode = if (r == Z_STREAM_END) WASH else BADCODE
            } else {
              need = lbits
              tree = ltree
              tree_index = ltree_index
              mode = LEN
            }
          } else {
            need = lbits
            tree = ltree
            tree_index = ltree_index
            mode = LEN
          }

        case LEN =>
          j = need
          while (k < j) {
            if (n != 0) r = Z_OK
            else {
              s.bitb = b; s.bitk = k
              z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
              s.write = q
              return s.inflate_flush(r)
            }
            n -= 1
            b |= (z.next_in(p) & 0xff) << k; p += 1
            k += 8
          }
          tindex = (tree_index + (b & inflate_mask(j))) * 3
          b >>>= tree(tindex + 1)
          k -= tree(tindex + 1)
          e = tree(tindex)
          if (e == 0) {
            lit = tree(tindex + 2)
            mode = LIT
          } else if ((e & 16) != 0) {
            get = e & 15
            len = tree(tindex + 2)
            mode = LENEXT
          } else if ((e & 64) == 0) {
            need = e
            tree_index = tindex / 3 + tree(tindex + 2)
            // mode stays LEN, loop continues
          } else if ((e & 32) != 0) {
            mode = WASH
          } else {
            mode = BADCODE
            z.msg = "invalid literal/length code"
            r = Z_DATA_ERROR
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            return s.inflate_flush(r)
          }

        case LENEXT =>
          j = get
          while (k < j) {
            if (n != 0) r = Z_OK
            else {
              s.bitb = b; s.bitk = k
              z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
              s.write = q
              return s.inflate_flush(r)
            }
            n -= 1; b |= (z.next_in(p) & 0xff) << k; p += 1
            k += 8
          }
          len += (b & inflate_mask(j))
          b >>= j
          k -= j
          need = dbits
          tree = dtree
          tree_index = dtree_index
          mode = DIST

        case DIST =>
          j = need
          while (k < j) {
            if (n != 0) r = Z_OK
            else {
              s.bitb = b; s.bitk = k
              z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
              s.write = q
              return s.inflate_flush(r)
            }
            n -= 1; b |= (z.next_in(p) & 0xff) << k; p += 1
            k += 8
          }
          tindex = (tree_index + (b & inflate_mask(j))) * 3
          b >>= tree(tindex + 1)
          k -= tree(tindex + 1)
          e = tree(tindex)
          if ((e & 16) != 0) {
            get = e & 15
            dist = tree(tindex + 2)
            mode = DISTEXT
          } else if ((e & 64) == 0) {
            need = e
            tree_index = tindex / 3 + tree(tindex + 2)
            // mode stays DIST, loop continues
          } else {
            mode = BADCODE
            z.msg = "invalid distance code"
            r = Z_DATA_ERROR
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            return s.inflate_flush(r)
          }

        case DISTEXT =>
          j = get
          while (k < j) {
            if (n != 0) r = Z_OK
            else {
              s.bitb = b; s.bitk = k
              z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
              s.write = q
              return s.inflate_flush(r)
            }
            n -= 1; b |= (z.next_in(p) & 0xff) << k; p += 1
            k += 8
          }
          dist += (b & inflate_mask(j))
          b >>= j
          k -= j
          mode = COPY

        case COPY =>
          f = q - dist
          while (f < 0) { f += s.end }
          while (len != 0) {
            if (m == 0) {
              if (q == s.end && s.read != 0) { q = 0; m = if (q < s.read) s.read - q - 1 else s.end - q }
              if (m == 0) {
                s.write = q; r = s.inflate_flush(r)
                q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q
                if (q == s.end && s.read != 0) { q = 0; m = if (q < s.read) s.read - q - 1 else s.end - q }
                if (m == 0) {
                  s.bitb = b; s.bitk = k
                  z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
                  s.write = q
                  return s.inflate_flush(r)
                }
              }
            }
            s.window(q) = s.window(f); q += 1; m -= 1; f += 1
            if (f == s.end) f = 0
            len -= 1
          }
          mode = START

        case LIT =>
          if (m == 0) {
            if (q == s.end && s.read != 0) { q = 0; m = if (q < s.read) s.read - q - 1 else s.end - q }
            if (m == 0) {
              s.write = q; r = s.inflate_flush(r)
              q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q
              if (q == s.end && s.read != 0) { q = 0; m = if (q < s.read) s.read - q - 1 else s.end - q }
              if (m == 0) {
                s.bitb = b; s.bitk = k
                z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
                s.write = q
                return s.inflate_flush(r)
              }
            }
          }
          r = Z_OK
          s.window(q) = lit.toByte; q += 1; m -= 1
          mode = START

        case WASH =>
          if (k > 7) {
            k -= 8
            n += 1
            p -= 1
          }
          s.write = q; r = s.inflate_flush(r)
          q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q
          if (s.read != s.write) {
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            return s.inflate_flush(r)
          }
          mode = END

        case END =>
          r = Z_STREAM_END
          s.bitb = b; s.bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          s.write = q
          return s.inflate_flush(r)

        case BADCODE =>
          r = Z_DATA_ERROR
          s.bitb = b; s.bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          s.write = q
          return s.inflate_flush(r)

        case _ =>
          r = Z_STREAM_ERROR
          s.bitb = b; s.bitk = k
          z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
          s.write = q
          return s.inflate_flush(r)
      }
    }
    r // unreachable; satisfies type checker
  }

  private[jzlib] def free(z: ZStream): Unit = {}

  // Called with number of bytes left to write in window at least 258
  // (the maximum string length) and number of input bytes available
  // at least ten.
  private def inflate_fast(
    bl: Int,
    bd: Int,
    tl: Array[Int],
    tl_index: Int,
    td: Array[Int],
    td_index: Int,
    s: InfBlocks,
    z: ZStream,
  ): Int = {
    var t: Int            = 0
    var tp: Array[Int]    = null
    var tp_index: Int     = 0
    var e: Int            = 0
    var b: Int            = 0
    var k: Int            = 0
    var p: Int            = 0
    var n: Int            = 0
    var q: Int            = 0
    var m: Int            = 0
    var ml: Int           = 0
    var md: Int           = 0
    var c: Int            = 0
    var d: Int            = 0
    var r: Int            = 0
    var tp_index_t_3: Int = 0

    p = z.next_in_index; n = z.avail_in; b = s.bitb; k = s.bitk
    q = s.write; m = if (q < s.read) s.read - q - 1 else s.end - q

    ml = inflate_mask(bl)
    md = inflate_mask(bd)

    var outerCond = true
    while (outerCond) {
      // get literal/length code (fill up to 20 bits)
      while (k < 20) {
        n -= 1
        b |= (z.next_in(p) & 0xff) << k; p += 1; k += 8
      }

      t = b & ml
      tp = tl; tp_index = tl_index
      tp_index_t_3 = (tp_index + t) * 3
      e = tp(tp_index_t_3)

      if (e == 0) {
        b >>>= tp(tp_index_t_3 + 1); k -= tp(tp_index_t_3 + 1)
        s.window(q) = tp(tp_index_t_3 + 2).toByte; q += 1
        m -= 1
        // continue: fall through to outer condition check
      } else {
        var innerDone1 = false
        while (!innerDone1) {
          b >>>= tp(tp_index_t_3 + 1); k -= tp(tp_index_t_3 + 1)

          if ((e & 16) != 0) {
            e &= 15
            c = tp(tp_index_t_3 + 2) + (b & inflate_mask(e))
            b >>= e; k -= e

            // decode distance base of block to copy
            while (k < 15) {
              n -= 1
              b |= (z.next_in(p) & 0xff) << k; p += 1; k += 8
            }

            t = b & md
            tp = td; tp_index = td_index
            tp_index_t_3 = (tp_index + t) * 3
            e = tp(tp_index_t_3)

            var innerDone2 = false
            while (!innerDone2) {
              b >>>= tp(tp_index_t_3 + 1); k -= tp(tp_index_t_3 + 1)

              if ((e & 16) != 0) {
                e &= 15
                while (k < e) {
                  n -= 1
                  b |= (z.next_in(p) & 0xff) << k; p += 1; k += 8
                }

                d = tp(tp_index_t_3 + 2) + (b & inflate_mask(e))
                b >>= e; k -= e

                m -= c
                if (q >= d) {
                  r = q - d
                  if (q - r > 0 && 2 > (q - r)) {
                    s.window(q) = s.window(r); q += 1; r += 1
                    s.window(q) = s.window(r); q += 1; r += 1
                    c -= 2
                  } else {
                    System.arraycopy(s.window, r, s.window, q, 2)
                    q += 2; r += 2; c -= 2
                  }
                } else {
                  r = q - d
                  while (r < 0) { r += s.end }
                  e = s.end - r
                  if (c > e) {
                    c -= e
                    if (q - r > 0 && e > (q - r)) {
                      while ({ s.window(q) = s.window(r); q += 1; r += 1; e -= 1; e != 0 }) ()
                    } else {
                      System.arraycopy(s.window, r, s.window, q, e)
                      q += e; r += e; e = 0
                    }
                    r = 0 // copy rest from start of window
                  }
                }

                // copy all or what's left
                if (q - r > 0 && c > (q - r)) {
                  while ({ s.window(q) = s.window(r); q += 1; r += 1; c -= 1; c != 0 }) ()
                } else {
                  System.arraycopy(s.window, r, s.window, q, c)
                  q += c; r += c; c = 0
                }
                innerDone2 = true
              } else if ((e & 64) == 0) {
                t += tp(tp_index_t_3 + 2)
                t += (b & inflate_mask(e))
                tp_index_t_3 = (tp_index + t) * 3
                e = tp(tp_index_t_3)
                // continue inner loop 2
              } else {
                z.msg = "invalid distance code"
                c = z.avail_in - n; c = if (k >> 3 < c) k >> 3 else c; n += c; p -= c; k -= c << 3
                s.bitb = b; s.bitk = k
                z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
                s.write = q
                return Z_DATA_ERROR
              }
            }
            innerDone1 = true

          } else if ((e & 64) == 0) {
            t += tp(tp_index_t_3 + 2)
            t += (b & inflate_mask(e))
            tp_index_t_3 = (tp_index + t) * 3
            e = tp(tp_index_t_3)
            if (e == 0) {
              b >>>= tp(tp_index_t_3 + 1); k -= tp(tp_index_t_3 + 1)
              s.window(q) = tp(tp_index_t_3 + 2).toByte; q += 1
              m -= 1
              innerDone1 = true
            }
            // else continue inner loop 1

          } else if ((e & 32) != 0) {
            c = z.avail_in - n; c = if (k >> 3 < c) k >> 3 else c; n += c; p -= c; k -= c << 3
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            return Z_STREAM_END

          } else {
            z.msg = "invalid literal/length code"
            c = z.avail_in - n; c = if (k >> 3 < c) k >> 3 else c; n += c; p -= c; k -= c << 3
            s.bitb = b; s.bitk = k
            z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
            s.write = q
            return Z_DATA_ERROR
          }
        }
      }

      outerCond = m >= 258 && n >= 10
    }

    // not enough input or output -- restore pointers and return
    c = z.avail_in - n; c = if (k >> 3 < c) k >> 3 else c; n += c; p -= c; k -= c << 3
    s.bitb = b; s.bitk = k
    z.avail_in = n; z.total_in += p - z.next_in_index; z.next_in_index = p
    s.write = q

    Z_OK
  }
}
