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

import java.io.{EOFException, FilterInputStream, IOException, InputStream}
import JZlib._

object InflaterInputStream {
  private[jzlib] final val DEFAULT_BUFSIZE = 512
}

/** Wraps an [[java.io.InputStream]] to decompress data compressed with the DEFLATE algorithm (RFC 1951).
  *
  * Reads from this stream return decompressed data. The compressed bytes are read from the
  * underlying input stream and inflated via the supplied [[Inflater]].
  *
  * By default the stream expects zlib-wrapped data (RFC 1950). Pass `nowrap = true` to decompress
  * raw DEFLATE data without a zlib header and trailer.
  *
  * Usage:
  * {{{
  * val fis = new java.io.FileInputStream("data.zlib")
  * val iis = new InflaterInputStream(fis)
  * val buf = new Array[Byte](1024)
  * var n = iis.read(buf)
  * while (n != -1) {
  *   System.out.write(buf, 0, n)
  *   n = iis.read(buf)
  * }
  * iis.close()
  * }}}
  *
  * @param in
  *   underlying input stream containing compressed data
  * @param inflater
  *   the [[Inflater]] used to decompress data
  * @param size
  *   internal buffer size in bytes (must be &gt; 0, default 512)
  * @param close_in
  *   whether [[close()]] should also close the underlying stream (default `true`)
  * @see [[DeflaterOutputStream]] for compression
  * @see [[GZIPInputStream]] for GZIP format decompression
  */
class InflaterInputStream(in: InputStream,
                           val inflater: Inflater,
                           size: Int,
                           close_in: Boolean)
  extends FilterInputStream(in) {

  if (in == null || inflater == null) throw new NullPointerException()
  else if (size <= 0) throw new IllegalArgumentException("buffer size must be greater than 0")

  protected var buf: Array[Byte] = new Array[Byte](size)

  private var closed: Boolean = false

  private var eof: Boolean = false

  protected var myinflater: Boolean = false

  private val byte1: Array[Byte] = new Array[Byte](1)

  /** Creates an `InflaterInputStream` with the default buffer size (512 bytes).
    *
    * @param in
    *   underlying input stream containing compressed data
    * @param nowrap
    *   if `true`, decompress raw DEFLATE data without a zlib header/trailer
    */
  def this(in: InputStream, nowrap: Boolean) = {
    this(in, new Inflater(nowrap), InflaterInputStream.DEFAULT_BUFSIZE, true)
    myinflater = true
  }

  /** Creates an `InflaterInputStream` expecting zlib-wrapped data with the default buffer size.
    *
    * @param in
    *   underlying input stream containing compressed data
    */
  def this(in: InputStream) = {
    this(in, false)
  }

  /** Creates an `InflaterInputStream` with the given inflater and default buffer size (512 bytes).
    *
    * @param in
    *   underlying input stream
    * @param inflater
    *   the [[Inflater]] to use
    */
  def this(in: InputStream, inflater: Inflater) =
    this(in, inflater, InflaterInputStream.DEFAULT_BUFSIZE, true)

  /** Creates an `InflaterInputStream` with the given inflater and buffer size.
    *
    * @param in
    *   underlying input stream
    * @param inflater
    *   the [[Inflater]] to use
    * @param size
    *   internal buffer size in bytes
    */
  def this(in: InputStream, inflater: Inflater, size: Int) =
    this(in, inflater, size, true)

  /** Reads a single decompressed byte, returning -1 at end of stream. */
  override def read(): Int = {
    if (closed) throw new IOException("Stream closed")
    if (read(byte1, 0, 1) == -1) -1 else byte1(0) & 0xff
  }

  /** Reads up to `len` decompressed bytes into `b` starting at `off`.
    *
    * @return
    *   the number of bytes actually read, or -1 at end of stream
    * @throws IOException
    *   if the compressed data is corrupt or an I/O error occurs
    */
  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (closed) throw new IOException("Stream closed")
    if (b == null) {
      throw new NullPointerException()
    } else if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException()
    } else if (len == 0) {
      return 0
    } else if (eof) {
      return -1
    }

    var n   = 0
    var off0 = off
    inflater.setOutput(b, off, len)
    while (!eof) {
      if (inflater.avail_in == 0)
        fill()
      val err = inflater.inflate(Z_NO_FLUSH)
      n   += inflater.next_out_index - off0
      off0 = inflater.next_out_index
      err match {
        case Z_DATA_ERROR =>
          throw new IOException(inflater.msg)
        case Z_STREAM_END | Z_NEED_DICT =>
          eof = true
          if (err == Z_NEED_DICT)
            return -1
        case _ =>
      }
      if (inflater.avail_out == 0)
        return n
    }
    n
  }

  /** Returns 0 after the end of the compressed data has been reached, 1 otherwise.
    *
    * Note: this does '''not''' return the number of bytes that can be read without blocking.
    */
  override def available(): Int = {
    if (closed) throw new IOException("Stream closed")
    if (eof) 0 else 1
  }

  private val skipBuf: Array[Byte] = new Array[Byte](512)

  override def skip(n: Long): Long = {
    if (n < 0)
      throw new IllegalArgumentException("negative skip length")
    if (closed) throw new IOException("Stream closed")

    val max   = Math.min(n, Integer.MAX_VALUE).toInt
    var total = 0
    while (total < max) {
      var len = max - total
      if (len > skipBuf.length) len = skipBuf.length
      len = read(skipBuf, 0, len)
      if (len == -1) {
        eof = true
        return total
      }
      total += len
    }
    total
  }

  /** Closes this stream and, if `close_in` is `true`, the underlying input stream. */
  override def close(): Unit = {
    if (!closed) {
      if (myinflater)
        inflater.end()
      if (close_in)
        in.close()
      closed = true
    }
  }

  protected def fill(): Unit = {
    if (closed) throw new IOException("Stream closed")
    val len = in.read(buf, 0, buf.length)
    if (len == -1) {
      if (inflater.istate.wrap == 0 && !inflater.finished()) {
        buf(0) = 0
        inflater.setInput(buf, 0, 1, true)
      } else if (inflater.istate.was != -1) { // in reading trailer
        throw new IOException("footer is not found")
      } else {
        throw new EOFException("Unexpected end of ZLIB input stream")
      }
    } else {
      inflater.setInput(buf, 0, len, true)
    }
  }

  override def markSupported(): Boolean = false

  override def mark(readlimit: Int): Unit = {}

  @throws[IOException]
  override def reset(): Unit =
    throw new IOException("mark/reset not supported")

  /** Returns the total number of compressed bytes read from the underlying stream. */
  def getTotalIn(): Long = inflater.getTotalIn

  /** Returns the total number of decompressed bytes produced. */
  def getTotalOut(): Long = inflater.getTotalOut

  /** Returns any unconsumed input bytes remaining in the inflater's buffer, or `null` if none. */
  def getAvailIn(): Array[Byte] = {
    if (inflater.avail_in <= 0)
      return null
    val tmp = new Array[Byte](inflater.avail_in)
    System.arraycopy(inflater.next_in, inflater.next_in_index, tmp, 0, inflater.avail_in)
    tmp
  }

  /** Reads the zlib or GZIP header from the underlying stream without decompressing data.
    *
    * This is useful when header metadata (e.g. GZIP filename, modification time) is needed before
    * reading the payload.
    *
    * @throws IOException
    *   if no input is available or the header is invalid
    */
  def readHeader(): Unit = {
    val empty = "".getBytes()
    inflater.setInput(empty, 0, 0, false)
    inflater.setOutput(empty, 0, 0)

    inflater.inflate(Z_NO_FLUSH)
    if (!inflater.istate.inParsingHeader()) {
      return
    }

    val b1 = new Array[Byte](1)
    do {
      val i = in.read(b1)
      if (i <= 0)
        throw new IOException("no input")
      inflater.setInput(b1)
      val err = inflater.inflate(Z_NO_FLUSH)
      if (err != 0 /*Z_OK*/)
        throw new IOException(inflater.msg)
    } while (inflater.istate.inParsingHeader())
  }

  /** Returns the underlying [[Inflater]] used by this stream. */
  def getInflater(): Inflater = inflater
}
