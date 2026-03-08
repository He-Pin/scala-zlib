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

private[jzlib] object Inflate {
  private final val MAX_WBITS = 15

  private final val PRESET_DICT = 0x20

  private final val Z_NO_FLUSH      = 0
  private final val Z_PARTIAL_FLUSH = 1
  private final val Z_SYNC_FLUSH    = 2
  private final val Z_FULL_FLUSH    = 3
  private final val Z_FINISH        = 4

  private final val Z_DEFLATED = 8

  private final val Z_OK            = 0
  private final val Z_STREAM_END    = 1
  private final val Z_NEED_DICT     = 2
  private final val Z_ERRNO         = -1
  private final val Z_STREAM_ERROR  = -2
  private final val Z_DATA_ERROR    = -3
  private final val Z_MEM_ERROR     = -4
  private final val Z_BUF_ERROR     = -5
  private final val Z_VERSION_ERROR = -6

  private final val METHOD = 0
  private final val FLAG   = 1
  private final val DICT4  = 2
  private final val DICT3  = 3
  private final val DICT2  = 4
  private final val DICT1  = 5
  private final val DICT0  = 6
  private final val BLOCKS = 7
  private final val CHECK4 = 8
  private final val CHECK3 = 9
  private final val CHECK2 = 10
  private final val CHECK1 = 11
  private final val DONE   = 12
  private final val BAD    = 13

  private final val HEAD    = 14
  private final val LENGTH  = 15
  private final val TIME    = 16
  private final val OS      = 17
  private final val EXLEN   = 18
  private final val EXTRA   = 19
  private final val NAME    = 20
  private final val COMMENT = 21
  private final val HCRC    = 22
  private final val FLAGS   = 23

  private[jzlib] final val INFLATE_ANY = 0x40000000

  private val mark: Array[Byte] = Array(0, 0, 0xff.toByte, 0xff.toByte)
}

private[jzlib] final class Inflate(private val z: ZStream) {
  import Inflate._

  var mode: Int             = 0
  var method: Int           = 0
  var was: Long             = -1L
  var need: Long            = 0L
  var marker: Int           = 0
  var wrap: Int             = 0
  var wbits: Int            = 0
  var blocks: InfBlocks     = null
  private var dictSize: Int = 0

  private var flags: Int          = 0
  private var need_bytes: Int     = -1
  private val crcbuf: Array[Byte] = new Array[Byte](4)

  var gheader: GZIPHeader = null

  private class Return(val r: Int) extends Exception

  private var tmp_string: scala.collection.mutable.ArrayBuffer[Byte] = null

  def inflateReset(): Int = {
    if (z == null) return Z_STREAM_ERROR

    z.total_in = 0; z.total_out = 0
    z.msg = null
    this.mode = HEAD
    this.need_bytes = -1
    this.dictSize = 0
    this.blocks.reset()
    Z_OK
  }

  def inflateEnd(): Int = {
    if (blocks != null) {
      blocks.free()
    }
    Z_OK
  }

  def inflateInit(w0: Int): Int = {
    var w = w0
    z.msg = null
    blocks = null

    wrap = 0
    if (w < 0) {
      w = -w
    } else if ((w & INFLATE_ANY) != 0) {
      wrap = 4
      w &= ~INFLATE_ANY
      if (w < 48)
        w &= 15
    } else if ((w & ~31) != 0) {
      wrap = 4
      w &= 15
    } else {
      wrap = (w >> 4) + 1
      if (w < 48)
        w &= 15
    }

    if (w < 8 || w > 15) {
      inflateEnd()
      return Z_STREAM_ERROR
    }
    if (blocks != null && wbits != w) {
      blocks.free()
      blocks = null
    }

    wbits = w

    this.blocks = new InfBlocks(z, 1 << w)

    inflateReset()

    Z_OK
  }

  def inflate(f: Int): Int = {
    var hold   = 0
    var r: Int = 0
    var b: Int = 0

    if (z == null || z.next_in == null) {
      if (f == Z_FINISH && this.mode == HEAD)
        return Z_OK
      return Z_STREAM_ERROR
    }

    val ff = if (f == Z_FINISH) Z_BUF_ERROR else Z_OK
    r = Z_BUF_ERROR

    try {
      while (true) {
        this.mode match {

          case HEAD =>
            if (wrap == 0) {
              this.mode = BLOCKS
            } else {
              r = readBytes(2, r, ff)

              if (
                (wrap == 4 || (wrap & 2) != 0) &&
                this.need == 0x8b1fL
              ) {
                if (wrap == 4) {
                  wrap = 2
                }
                z.adler = new CRC32()
                checksum(2, this.need)

                if (gheader == null)
                  gheader = new GZIPHeader()

                this.mode = FLAGS
              } else if ((wrap & 2) != 0) {
                this.mode = BAD
                z.msg = "incorrect header check"
              } else {
                flags = 0

                this.method = this.need.toInt & 0xff
                b = ((this.need >> 8).toInt) & 0xff

                if (
                  ((wrap & 1) == 0 ||
                    (((this.method << 8) + b) % 31) != 0) &&
                  (this.method & 0xf) != Z_DEFLATED
                ) {
                  if (wrap == 4) {
                    z.next_in_index -= 2
                    z.avail_in += 2
                    z.total_in -= 2
                    wrap = 0
                    this.mode = BLOCKS
                  } else {
                    this.mode = BAD
                    z.msg = "incorrect header check"
                  }
                } else if ((this.method & 0xf) != Z_DEFLATED) {
                  this.mode = BAD
                  z.msg = "unknown compression method"
                } else {
                  if (wrap == 4) {
                    wrap = 1
                  }

                  if ((this.method >> 4) + 8 > this.wbits) {
                    this.mode = BAD
                    z.msg = "invalid window size"
                  } else {
                    z.adler = new Adler32()

                    if ((b & PRESET_DICT) == 0) {
                      this.mode = BLOCKS
                    } else {
                      this.mode = DICT4
                    }
                  }
                }
              }
            }

          case DICT4 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need = ((z.next_in(z.next_in_index) & 0xff).toLong << 24) & 0xff000000L
            z.next_in_index += 1
            this.mode = DICT3

          case DICT3 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += ((z.next_in(z.next_in_index) & 0xff).toLong << 16) & 0xff0000L
            z.next_in_index += 1
            this.mode = DICT2

          case DICT2 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += ((z.next_in(z.next_in_index) & 0xff).toLong << 8) & 0xff00L
            z.next_in_index += 1
            this.mode = DICT1

          case DICT1 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += (z.next_in(z.next_in_index) & 0xffL)
            z.next_in_index += 1
            z.adler.reset(this.need)
            this.mode = DICT0
            return Z_NEED_DICT

          case DICT0 =>
            this.mode = BAD
            z.msg = "need dictionary"
            this.marker = 0
            return Z_STREAM_ERROR

          case BLOCKS =>
            r = this.blocks.proc(r)
            if (r == Z_DATA_ERROR) {
              this.mode = BAD
              this.marker = 0
            } else {
              if (r == Z_OK) {
                r = ff
              }
              if (r != Z_STREAM_END) {
                return r
              }
              r = ff
              this.was = z.adler.getValue
              this.blocks.reset()
              if (this.wrap == 0) {
                this.mode = DONE
              } else {
                this.mode = CHECK4
              }
            }

          case CHECK4 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need = ((z.next_in(z.next_in_index) & 0xff).toLong << 24) & 0xff000000L
            z.next_in_index += 1
            this.mode = CHECK3

          case CHECK3 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += ((z.next_in(z.next_in_index) & 0xff).toLong << 16) & 0xff0000L
            z.next_in_index += 1
            this.mode = CHECK2

          case CHECK2 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += ((z.next_in(z.next_in_index) & 0xff).toLong << 8) & 0xff00L
            z.next_in_index += 1
            this.mode = CHECK1

          case CHECK1 =>
            if (z.avail_in == 0) throw new Return(r)
            r = ff

            z.avail_in -= 1; z.total_in += 1
            this.need += (z.next_in(z.next_in_index) & 0xffL)
            z.next_in_index += 1

            if (flags != 0) {
              this.need =
                ((this.need & 0xff000000L) >> 24 |
                  (this.need & 0x00ff0000L) >> 8 |
                  (this.need & 0x0000ff00L) << 8 |
                  (this.need & 0x000000ffL) << 24) & 0xffffffffL
            }

            if (this.was.toInt != this.need.toInt) {
              z.msg = "incorrect data check"
            } else if (flags != 0 && gheader != null) {
              gheader.crc = this.need
            }

            this.mode = LENGTH

          case LENGTH =>
            if (wrap != 0 && flags != 0) {
              r = readBytes(4, r, ff)

              if (z.msg != null && z.msg.equals("incorrect data check")) {
                this.mode = BAD
                this.marker = 5
              } else if (this.need != (z.total_out & 0xffffffffL)) {
                z.msg = "incorrect length check"
                this.mode = BAD
              } else {
                z.msg = null
                this.mode = DONE
              }
            } else {
              if (z.msg != null && z.msg.equals("incorrect data check")) {
                this.mode = BAD
                this.marker = 5
              } else {
                this.mode = DONE
              }
            }

          case DONE =>
            return Z_STREAM_END

          case BAD =>
            return Z_DATA_ERROR

          case FLAGS =>
            r = readBytes(2, r, ff)

            flags = this.need.toInt & 0xffff

            if ((flags & 0xff) != Z_DEFLATED) {
              z.msg = "unknown compression method"
              this.mode = BAD
            } else if ((flags & 0xe000) != 0) {
              z.msg = "unknown header flags set"
              this.mode = BAD
            } else {
              if ((flags & 0x0200) != 0) {
                checksum(2, this.need)
              }
              this.mode = TIME
            }

          case TIME =>
            r = readBytes(4, r, ff)
            if (gheader != null) {
              gheader.setModifiedTime(this.need)
            }
            if ((flags & 0x0200) != 0) {
              checksum(4, this.need)
            }
            this.mode = OS

          case OS =>
            r = readBytes(2, r, ff)
            if (gheader != null) {
              gheader.xflags = this.need.toInt & 0xff
              gheader.os = (this.need.toInt >> 8) & 0xff
            }
            if ((flags & 0x0200) != 0) {
              checksum(2, this.need)
            }
            this.mode = EXLEN

          case EXLEN =>
            if ((flags & 0x0400) != 0) {
              r = readBytes(2, r, ff)
              if (gheader != null) {
                gheader.extra = new Array[Byte](this.need.toInt & 0xffff)
              }
              if ((flags & 0x0200) != 0) {
                checksum(2, this.need)
              }
            } else if (gheader != null) {
              gheader.extra = null
            }
            this.mode = EXTRA

          case EXTRA =>
            if ((flags & 0x0400) != 0) {
              r = readBytes(r, ff)
              if (gheader != null) {
                val foo = tmp_string.toArray
                tmp_string = null
                if (foo.length == gheader.extra.length) {
                  System.arraycopy(foo, 0, gheader.extra, 0, foo.length)
                } else {
                  z.msg = "bad extra field length"
                  this.mode = BAD
                }
              }
            } else if (gheader != null) {
              gheader.extra = null
            }
            if (this.mode != BAD) {
              this.mode = NAME
            }

          case NAME =>
            if ((flags & 0x0800) != 0) {
              r = readString(r, ff)
              if (gheader != null) {
                gheader.name = tmp_string.toArray
              }
              tmp_string = null
            } else if (gheader != null) {
              gheader.name = null
            }
            this.mode = COMMENT

          case COMMENT =>
            if ((flags & 0x1000) != 0) {
              r = readString(r, ff)
              if (gheader != null) {
                gheader.comment = tmp_string.toArray
              }
              tmp_string = null
            } else if (gheader != null) {
              gheader.comment = null
            }
            this.mode = HCRC

          case HCRC =>
            if ((flags & 0x0200) != 0) {
              r = readBytes(2, r, ff)
              if (gheader != null) {
                gheader.hcrc = (this.need & 0xffff).toInt
              }
              if (this.need != (z.adler.getValue & 0xffffL)) {
                this.mode = BAD
                z.msg = "header crc mismatch"
                this.marker = 5
              }
            }
            if (this.mode != BAD) {
              z.adler = new CRC32()
              this.mode = BLOCKS
            }

          case _ =>
            return Z_STREAM_ERROR
        }
      }
    } catch {
      case e: Return => return e.r
    }
    Z_STREAM_ERROR
  }

  def inflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (z == null || (this.mode != DICT0 && this.wrap != 0)) {
      return Z_STREAM_ERROR
    }

    var index  = 0
    var length = dictLength

    if (this.mode == DICT0) {
      val adler_need = z.adler.getValue
      z.adler.reset()
      z.adler.update(dictionary, 0, dictLength)
      if (z.adler.getValue != adler_need) {
        return Z_DATA_ERROR
      }
    }

    z.adler.reset()

    if (length >= (1 << this.wbits)) {
      length = (1 << this.wbits) - 1
      index = dictLength - length
    }
    this.blocks.set_dictionary(dictionary, index, length)
    this.dictSize = length
    this.mode = BLOCKS
    Z_OK
  }

  def inflateGetDictionary(
    dictionary: Array[Byte],
    dictLength: Array[Int],
  ): Int = {
    if (z == null) return Z_STREAM_ERROR
    if (blocks == null || blocks.window == null) return Z_STREAM_ERROR
    val whave =
      math.min(z.total_out + dictSize.toLong, blocks.end.toLong).toInt
    if (whave > 0 && dictionary != null) {
      val wnext = blocks.write
      System.arraycopy(blocks.window, wnext, dictionary, 0, whave - wnext)
      if (wnext > 0)
        System.arraycopy(blocks.window, 0, dictionary, whave - wnext, wnext)
    }
    if (dictLength != null)
      dictLength(0) = whave
    Z_OK
  }

  def inflateSync(): Int = {
    var n: Int  = 0
    var p: Int  = 0
    var m: Int  = 0
    var r: Long = 0L
    var w: Long = 0L

    if (z == null)
      return Z_STREAM_ERROR
    if (this.mode != BAD) {
      this.mode = BAD
      this.marker = 0
    }
    n = z.avail_in
    if (n == 0)
      return Z_BUF_ERROR

    p = z.next_in_index
    m = this.marker
    while (n != 0 && m < 4) {
      if (z.next_in(p) == mark(m)) {
        m += 1
      } else if (z.next_in(p) != 0) {
        m = 0
      } else {
        m = 4 - m
      }
      p += 1; n -= 1
    }

    z.total_in += (p - z.next_in_index).toLong
    z.next_in_index = p
    z.avail_in = n
    this.marker = m

    if (m != 4) {
      return Z_DATA_ERROR
    }
    r = z.total_in; w = z.total_out
    inflateReset()
    z.total_in = r; z.total_out = w
    this.mode = BLOCKS

    Z_OK
  }

  def inflateSyncPoint(): Int = {
    if (z == null || this.blocks == null)
      return Z_STREAM_ERROR
    this.blocks.sync_point()
  }

  private def readBytes(n: Int, r0: Int, f: Int): Int = {
    var r = r0
    if (need_bytes == -1) {
      need_bytes = n
      this.need = 0
    }
    while (need_bytes > 0) {
      if (z.avail_in == 0) { throw new Return(r) }
      r = f
      z.avail_in -= 1; z.total_in += 1
      this.need = this.need |
        ((z.next_in(z.next_in_index) & 0xff).toLong << ((n - need_bytes) * 8))
      z.next_in_index += 1
      need_bytes -= 1
    }
    if (n == 2) {
      this.need &= 0xffffL
    } else if (n == 4) {
      this.need &= 0xffffffffL
    }
    need_bytes = -1
    r
  }

  private def readString(r0: Int, f: Int): Int = {
    if (tmp_string == null) tmp_string = new scala.collection.mutable.ArrayBuffer[Byte]()
    var r    = r0
    var b    = 0
    var cont = true
    while (cont) {
      if (z.avail_in == 0) throw new Return(r)
      r = f
      z.avail_in -= 1; z.total_in += 1
      b = z.next_in(z.next_in_index)
      if (b != 0) tmp_string += z.next_in(z.next_in_index)
      z.adler.update(z.next_in, z.next_in_index, 1)
      z.next_in_index += 1
      cont = b != 0
    }
    r
  }

  private def readBytes(r0: Int, f: Int): Int = {
    if (tmp_string == null) tmp_string = new scala.collection.mutable.ArrayBuffer[Byte]()
    var r = r0
    while (this.need > 0) {
      if (z.avail_in == 0) throw new Return(r)
      r = f
      z.avail_in -= 1; z.total_in += 1
      tmp_string += z.next_in(z.next_in_index)
      z.adler.update(z.next_in, z.next_in_index, 1)
      z.next_in_index += 1
      this.need -= 1
    }
    r
  }

  private def checksum(n: Int, v0: Long): Unit = {
    var v = v0
    var i = 0
    while (i < n) {
      crcbuf(i) = (v & 0xff).toByte
      v >>= 8
      i += 1
    }
    z.adler.update(crcbuf, 0, n)
  }

  def getGZIPHeader: GZIPHeader = gheader

  def inParsingHeader(): Boolean = {
    mode match {
      case HEAD | DICT4 | DICT3 | DICT2 | DICT1 |
          FLAGS | TIME | OS | EXLEN | EXTRA |
          NAME | COMMENT | HCRC =>
        true
      case _ =>
        false
    }
  }
}
