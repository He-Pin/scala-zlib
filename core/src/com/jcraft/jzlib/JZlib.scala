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

object JZlib {

  private val _version  = "1.1.0"
  def version(): String = _version

  final val MAX_WBITS = 15 // 32K LZ77 window
  final val DEF_WBITS = MAX_WBITS

  // WrapperType: cross-platform sealed hierarchy (replaces Java enum)
  sealed abstract class WrapperType
  object WrapperType {
    case object NONE extends WrapperType
    case object ZLIB extends WrapperType
    case object GZIP extends WrapperType
    case object ANY  extends WrapperType
  }

  val W_NONE: WrapperType = WrapperType.NONE
  val W_ZLIB: WrapperType = WrapperType.ZLIB
  val W_GZIP: WrapperType = WrapperType.GZIP
  val W_ANY: WrapperType  = WrapperType.ANY

  // compression levels
  final val Z_NO_COMPRESSION      = 0
  final val Z_BEST_SPEED          = 1
  final val Z_BEST_COMPRESSION    = 9
  final val Z_DEFAULT_COMPRESSION = -1

  // compression strategy
  final val Z_FILTERED         = 1
  final val Z_HUFFMAN_ONLY     = 2
  final val Z_DEFAULT_STRATEGY = 0

  final val Z_NO_FLUSH      = 0
  final val Z_PARTIAL_FLUSH = 1
  final val Z_SYNC_FLUSH    = 2
  final val Z_FULL_FLUSH    = 3
  final val Z_FINISH        = 4

  final val Z_OK            = 0
  final val Z_STREAM_END    = 1
  final val Z_NEED_DICT     = 2
  final val Z_ERRNO         = -1
  final val Z_STREAM_ERROR  = -2
  final val Z_DATA_ERROR    = -3
  final val Z_MEM_ERROR     = -4
  final val Z_BUF_ERROR     = -5
  final val Z_VERSION_ERROR = -6

  // The three kinds of block type
  final val Z_BINARY: Byte  = 0
  final val Z_ASCII: Byte   = 1
  final val Z_UNKNOWN: Byte = 2

  def adler32_combine(adler1: Long, adler2: Long, len2: Long): Long =
    Adler32.combine(adler1, adler2, len2)

  def crc32_combine(crc1: Long, crc2: Long, len2: Long): Long =
    CRC32.combine(crc1, crc2, len2)
}

// Kept for source compatibility – the companion object holds the real API.
final class JZlib private ()
