/* -*-mode:scala; c-basic-offset:2; indent-tabs-mode:nil -*- */
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

package com.jcraft.jzlib

import java.io.{IOException, InputStream}
import JZlib._

/** Wraps an [[java.io.InputStream]] to decompress GZIP-compressed data (RFC 1952).
  *
  * Extends [[InflaterInputStream]] with GZIP header and trailer parsing. After decompression is
  * complete, GZIP metadata (filename, comment, modification time, OS, CRC-32) is available via
  * accessor methods.
  *
  * Usage:
  * {{{
  * val fis = new java.io.FileInputStream("data.gz")
  * val gis = new GZIPInputStream(fis)
  * val buf = new Array[Byte](1024)
  * var n = gis.read(buf)
  * while (n != -1) {
  *   System.out.write(buf, 0, n)
  *   n = gis.read(buf)
  * }
  * println("Original file: " + gis.getName())
  * gis.close()
  * }}}
  *
  * @param in
  *   underlying input stream containing GZIP-compressed data
  * @param inflater
  *   the [[Inflater]] used for decompression (must be configured with GZIP wrapping, i.e. `wbits = 15 + 16`)
  * @param size
  *   internal buffer size in bytes (default 512)
  * @param close_in
  *   whether [[close()]] should also close the underlying stream (default `true`)
  * @see [[GZIPOutputStream]] for compression
  * @see [[InflaterInputStream]] for zlib-wrapped decompression
  */
class GZIPInputStream(in: InputStream,
                       inflater: Inflater,
                       size: Int,
                       close_in: Boolean)
  extends InflaterInputStream(in, inflater, size, close_in) {

  /** Creates a `GZIPInputStream` with the given buffer size and close behavior.
    *
    * An [[Inflater]] configured for GZIP wrapping is created automatically.
    *
    * @param in
    *   underlying input stream
    * @param size
    *   internal buffer size in bytes
    * @param close_in
    *   whether [[close()]] should also close the underlying stream
    */
  def this(in: InputStream, size: Int, close_in: Boolean) = {
    this(in, new Inflater(15 + 16), size, close_in)
    myinflater = true
  }

  /** Creates a `GZIPInputStream` with the default buffer size (512 bytes).
    *
    * @param in
    *   underlying input stream containing GZIP-compressed data
    */
  def this(in: InputStream) =
    this(in, InflaterInputStream.DEFAULT_BUFSIZE, true)

  /** Returns the modification time from the GZIP header, in seconds since the Unix epoch.
    *
    * @deprecated Use [[getModifiedTime()]] instead (note the capital 'T').
    */
  @deprecated("Use getModifiedTime()", "1.1.5")
  def getModifiedtime(): Long = this.getModifiedTime()

  /** Returns the modification time from the GZIP header, in seconds since the Unix epoch. */
  def getModifiedTime(): Long =
    inflater.istate.getGZIPHeader.getModifiedTime

  /** Returns the OS field from the GZIP header (see RFC 1952 section 2.3.1). */
  def getOS(): Int =
    inflater.istate.getGZIPHeader.getOS

  /** Returns the original filename from the GZIP header, or `null` if not set. */
  def getName(): String =
    inflater.istate.getGZIPHeader.getName

  /** Returns the comment from the GZIP header, or `null` if not set. */
  def getComment(): String =
    inflater.istate.getGZIPHeader.getComment

  /** Returns the CRC-32 checksum of the decompressed data.
    *
    * Available only after all data has been read (i.e. [[read()]] has returned -1).
    *
    * @throws GZIPException
    *   if decompression is not yet complete
    */
  def getCRC(): Long = {
    if (inflater.istate.mode != 12 /*DONE*/)
      throw new GZIPException("checksum is not calculated yet.")
    inflater.istate.getGZIPHeader.getCRC
  }

  override def readHeader(): Unit = {
    val empty = "".getBytes()
    inflater.setOutput(empty, 0, 0)
    inflater.setInput(empty, 0, 0, false)

    val b = new Array[Byte](10)

    val n = fillBuf(b)
    if (n != 10) {
      if (n > 0) {
        inflater.setInput(b, 0, n, false)
        inflater.next_in_index = 0
        inflater.avail_in = n
      }
      throw new IOException("no input")
    }

    inflater.setInput(b, 0, n, false)

    val b1 = new Array[Byte](1)
    do {
      if (inflater.avail_in <= 0) {
        val i = in.read(b1)
        if (i <= 0)
          throw new IOException("no input")
        inflater.setInput(b1, 0, 1, true)
      }

      val err = inflater.inflate(Z_NO_FLUSH)

      if (err != 0 /*Z_OK*/) {
        val len = 2048 - inflater.next_in.length
        if (len > 0) {
          val tmp = new Array[Byte](len)
          val nn  = fillBuf(tmp)
          if (nn > 0) {
            inflater.avail_in     += inflater.next_in_index
            inflater.next_in_index = 0
            inflater.setInput(tmp, 0, nn, true)
          }
        }
        inflater.avail_in     += inflater.next_in_index
        inflater.next_in_index = 0
        throw new IOException(inflater.msg)
      }
    } while (inflater.istate.inParsingHeader())
  }

  private def fillBuf(buf: Array[Byte]): Int = {
    val len = buf.length
    var n   = 0
    var cont = true
    while (cont) {
      var i = -1
      try {
        i = in.read(buf, n, buf.length - n)
      } catch {
        case _: IOException =>
      }
      if (i == -1) {
        cont = false
      } else {
        n += i
        if (n >= len) cont = false
      }
    }
    n
  }
}
