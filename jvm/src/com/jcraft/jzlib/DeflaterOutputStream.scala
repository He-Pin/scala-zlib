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

import java.io.{ FilterOutputStream, IOException, OutputStream }
import JZlib._

object DeflaterOutputStream {
  private[jzlib] final val DEFAULT_BUFSIZE = 512
}

/**
 * Wraps an [[java.io.OutputStream]] to compress written data using the DEFLATE algorithm (RFC 1951).
 *
 * All data written to this stream is compressed via the supplied [[Deflater]] and forwarded to the underlying output
 * stream. Call [[finish()]] to complete the compressed stream before closing.
 *
 * By default the stream uses zlib wrapping (RFC 1950). For raw deflate or GZIP wrapping, supply a [[Deflater]]
 * configured with the desired wrapper type.
 *
 * Usage:
 * {{{
 * val fos = new java.io.FileOutputStream("data.zlib")
 * val dos = new DeflaterOutputStream(fos)
 * dos.write("Hello, World!".getBytes("UTF-8"))
 * dos.finish()
 * dos.close()
 * }}}
 *
 * @param out
 *   underlying output stream to write compressed data to
 * @param deflater
 *   the [[Deflater]] used to compress data
 * @param size
 *   internal buffer size in bytes (must be &gt; 0, default 512)
 * @param close_out
 *   whether [[close()]] should also close the underlying stream (default `true`)
 * @see
 *   [[InflaterInputStream]] for decompression
 * @see
 *   [[GZIPOutputStream]] for GZIP format compression
 */
class DeflaterOutputStream(out: OutputStream, val deflater: Deflater, size: Int, close_out: Boolean)
    extends FilterOutputStream(out) {

  if (out == null || deflater == null) throw new NullPointerException()
  else if (size <= 0) throw new IllegalArgumentException("buffer size must be greater than 0")

  protected var buffer: Array[Byte] = new Array[Byte](size)

  private var closed: Boolean = false

  private var syncFlush: Boolean = false

  private val buf1: Array[Byte] = new Array[Byte](1)

  protected var mydeflater: Boolean = false

  /**
   * Creates a `DeflaterOutputStream` with default compression level and buffer size (512 bytes).
   *
   * @param out
   *   underlying output stream
   */
  def this(out: OutputStream) = {
    this(out, new Deflater(JZlib.Z_DEFAULT_COMPRESSION), DeflaterOutputStream.DEFAULT_BUFSIZE, true)
    mydeflater = true
  }

  /**
   * Creates a `DeflaterOutputStream` with the given deflater and default buffer size (512 bytes).
   *
   * @param out
   *   underlying output stream
   * @param deflater
   *   the [[Deflater]] to use
   */
  def this(out: OutputStream, deflater: Deflater) =
    this(out, deflater, DeflaterOutputStream.DEFAULT_BUFSIZE, true)

  /**
   * Creates a `DeflaterOutputStream` with the given deflater and buffer size.
   *
   * @param out
   *   underlying output stream
   * @param deflater
   *   the [[Deflater]] to use
   * @param size
   *   internal buffer size in bytes
   */
  def this(out: OutputStream, deflater: Deflater, size: Int) =
    this(out, deflater, size, true)

  /** Writes a single byte to the compressed stream. */
  override def write(b: Int): Unit = {
    buf1(0) = (b & 0xff).toByte
    write(buf1, 0, 1)
  }

  /**
   * Writes `len` bytes from the byte array `b` starting at offset `off` to the compressed stream.
   *
   * @throws IOException
   *   if the deflater has already finished or a deflation error occurs
   */
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (deflater.finished()) {
      throw new IOException("finished")
    } else if (off < 0 | len < 0 | off + len > b.length) {
      throw new IndexOutOfBoundsException()
    } else if (len == 0) {
      return
    } else {
      val flush = if (syncFlush) Z_SYNC_FLUSH else Z_NO_FLUSH
      deflater.setInput(b, off, len, true)
      while (deflater.avail_in > 0) {
        val err = deflate(flush)
        if (err == Z_STREAM_END)
          return
      }
    }
  }

  /**
   * Finishes writing compressed data without closing the underlying stream.
   *
   * Flushes all remaining compressed data and writes the stream trailer. After calling this method, no more data can be
   * written. Call this before [[close()]] when multiple compressed streams are written to the same underlying output.
   */
  def finish(): Unit = {
    while (!deflater.finished()) {
      deflate(Z_FINISH)
    }
  }

  /** Finishes compression and closes this stream and, if `close_out` is `true`, the underlying stream. */
  override def close(): Unit = {
    if (!closed) {
      finish()
      if (mydeflater) {
        deflater.end()
      }
      if (close_out)
        out.close()
      closed = true
    }
  }

  protected def deflate(flush: Int): Int = {
    deflater.setOutput(buffer, 0, buffer.length)
    val err = deflater.deflate(flush)
    err match {
      case Z_OK | Z_STREAM_END                                        =>
      // ok
      case Z_BUF_ERROR if deflater.avail_in <= 0 && flush != Z_FINISH =>
      // flush() without any data
      case _                                                          =>
        throw new IOException("failed to deflate: error=" + err + " avail_out=" + deflater.avail_out)
    }
    val len = deflater.next_out_index
    if (len > 0) {
      out.write(buffer, 0, len)
    }
    err
  }

  override def flush(): Unit = {
    if (syncFlush && !deflater.finished()) {
      var continue = true
      while (continue) {
        val err = deflate(Z_SYNC_FLUSH)
        if (deflater.next_out_index < buffer.length)
          continue = false
        else if (err == Z_STREAM_END)
          continue = false
      }
    }
    out.flush()
  }

  /** Returns the total number of uncompressed bytes written to this stream. */
  def getTotalIn(): Long = deflater.getTotalIn

  /** Returns the total number of compressed bytes written to the underlying stream. */
  def getTotalOut(): Long = deflater.getTotalOut

  /**
   * Enables or disables sync-flush mode.
   *
   * When enabled, [[flush()]] uses `Z_SYNC_FLUSH` so the compressed output is aligned to a byte boundary, allowing the
   * receiver to decompress all data written so far.
   */
  def setSyncFlush(syncFlush: Boolean): Unit =
    this.syncFlush = syncFlush

  /** Returns whether sync-flush mode is enabled. */
  def getSyncFlush(): Boolean = this.syncFlush

  /** Returns the underlying [[Deflater]] used by this stream. */
  def getDeflater(): Deflater = deflater
}
