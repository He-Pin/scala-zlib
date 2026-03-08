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

import java.io.{ IOException, OutputStream }
import JZlib._

/**
 * Wraps an [[java.io.OutputStream]] to compress written data in GZIP format (RFC 1952).
 *
 * Extends [[DeflaterOutputStream]] with GZIP header and trailer handling. The GZIP header fields (filename, comment,
 * modification time, OS) can be set before writing any data.
 *
 * Usage:
 * {{{
 * val fos = new java.io.FileOutputStream("data.gz")
 * val gos = new GZIPOutputStream(fos)
 * gos.setName("data.txt")
 * gos.setComment("example file")
 * gos.write("Hello, World!".getBytes("UTF-8"))
 * gos.finish()
 * gos.close()
 * }}}
 *
 * @param out
 *   underlying output stream to write GZIP-compressed data to
 * @param deflater
 *   the [[Deflater]] used for compression (must be configured with GZIP wrapping, i.e. `wbits = 15 + 16`)
 * @param size
 *   internal buffer size in bytes (default 512)
 * @param close_out
 *   whether [[close()]] should also close the underlying stream (default `true`)
 * @see
 *   [[GZIPInputStream]] for decompression
 * @see
 *   [[DeflaterOutputStream]] for zlib-wrapped compression
 */
class GZIPOutputStream(out: OutputStream, deflater: Deflater, size: Int, close_out: Boolean)
    extends DeflaterOutputStream(out, deflater, size, close_out) {

  /**
   * Creates a `GZIPOutputStream` with GZIP wrapping (RFC 1952), `Z_DEFAULT_COMPRESSION`, and the given buffer size. Set
   * `close_out` to control whether [[close()]] closes the underlying stream.
   */
  def this(out: OutputStream, size: Int, close_out: Boolean) = {
    this(out, new Deflater(Z_DEFAULT_COMPRESSION, 15 + 16), size, close_out)
    mydeflater = true
  }

  /**
   * Creates a `GZIPOutputStream` with GZIP wrapping (RFC 1952), `Z_DEFAULT_COMPRESSION`, and the given buffer size.
   * Closes the underlying stream on [[close()]].
   */
  def this(out: OutputStream, size: Int) =
    this(out, size, true)

  /**
   * Creates a `GZIPOutputStream` with GZIP wrapping (RFC 1952), `Z_DEFAULT_COMPRESSION`, and a 512-byte buffer. Closes
   * the underlying stream on [[close()]].
   */
  def this(out: OutputStream) =
    this(out, DeflaterOutputStream.DEFAULT_BUFSIZE)

  private def check(): Unit = {
    if (deflater.dstate.status != 42 /*INIT_STATUS*/ )
      throw new GZIPException("header is already written.")
  }

  /**
   * Sets the modification time in the GZIP header. Must be called before writing any data.
   *
   * @param mtime
   *   modification time in seconds since the Unix epoch
   * @throws GZIPException
   *   if data has already been written
   */
  def setModifiedTime(mtime: Long): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setModifiedTime(mtime)
  }

  /**
   * Sets the OS field in the GZIP header. Must be called before writing any data.
   *
   * @param os
   *   OS identifier (see RFC 1952 section 2.3.1)
   * @throws GZIPException
   *   if data has already been written
   */
  def setOS(os: Int): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setOS(os)
  }

  /**
   * Sets the original filename in the GZIP header. Must be called before writing any data.
   *
   * @param name
   *   the original filename
   * @throws GZIPException
   *   if data has already been written
   */
  def setName(name: String): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setName(name)
  }

  /**
   * Sets the comment in the GZIP header. Must be called before writing any data.
   *
   * @param comment
   *   the comment string
   * @throws GZIPException
   *   if data has already been written
   */
  def setComment(comment: String): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setComment(comment)
  }

  // Cached after finish() so getCRC() works even after close() clears dstate
  private var _crc: Long = -1L

  override def finish(): Unit = {
    super.finish()
    if (deflater.dstate != null && deflater.dstate.status == 666 /*FINISH_STATE*/ )
      _crc = deflater.dstate.getGZIPHeader().getCRC
  }

  /**
   * Returns the CRC-32 checksum of the uncompressed data.
   *
   * This value is available after [[finish()]] has been called. It can also be retrieved after [[close()]] since the
   * CRC is cached internally.
   *
   * @throws GZIPException
   *   if the stream has not been finished yet
   */
  def getCRC(): Long = {
    if (_crc >= 0) return _crc
    if (deflater.dstate == null || deflater.dstate.status != 666 /*FINISH_STATE*/ )
      throw new GZIPException("checksum is not calculated yet.")
    deflater.dstate.getGZIPHeader().getCRC
  }
}
