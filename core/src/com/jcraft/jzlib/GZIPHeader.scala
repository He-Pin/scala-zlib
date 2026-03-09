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

/**
 * Represents GZIP header metadata as defined in RFC 1952.
 *
 * Stores optional fields that can appear in a GZIP header: original file name, comment, modification time, operating
 * system identifier, extra data, and header CRC.
 *
 * String fields (name, comment) are encoded using ISO-8859-1 as required by the GZIP specification.
 *
 * ==Usage with Deflater==
 * {{{
 * val header = new GZIPHeader()
 * header.setName("data.txt")
 * header.setComment("compressed data")
 * header.setOS(GZIPHeader.OS_UNIX)
 * header.setModifiedTime(System.currentTimeMillis() / 1000)
 * }}}
 *
 * ==OS constants==
 * Use the companion object's `OS_*` constants (e.g., [[GZIPHeader.OS_UNIX]], [[GZIPHeader.OS_WIN32]]) for the `setOS`
 * method.
 *
 * @see
 *   [[GZIPHeader$]] for OS constants
 * @see
 *   [[Deflater]] for compression with GZIP wrapping
 */
class GZIPHeader extends Cloneable {
  import GZIPHeader._

  var text: Boolean        = false
  var fhcrc: Boolean       = false
  var xflags: Int          = 0
  var os: Int              = 255
  var extra: Array[Byte]   = null
  var name: Array[Byte]    = null
  var comment: Array[Byte] = null
  var hcrc: Int            = 0
  var crc: Long            = 0L
  var done: Boolean        = false
  var mtime: Long          = 0L

  /** Sets the file modification time (Unix timestamp in seconds). */
  def setModifiedTime(mtime: Long): Unit = this.mtime = mtime

  /** Returns the file modification time (Unix timestamp in seconds). */
  def getModifiedTime: Long = mtime

  /**
   * Sets the operating system on which the file was compressed.
   *
   * Valid values are 0-13 or 255 ([[GZIPHeader.OS_UNKNOWN]]). Use the `OS_*` constants from the [[GZIPHeader$]]
   * companion object.
   *
   * @param os
   *   the OS identifier
   * @throws IllegalArgumentException
   *   if `os` is not a valid value
   */
  def setOS(os: Int): Unit = {
    if ((0 <= os && os <= 13) || os == 255) this.os = os
    else throw new IllegalArgumentException("os: " + os)
  }

  /** Returns the OS identifier. */
  def getOS: Int = os

  /**
   * Detects the current operating system and sets the OS field automatically.
   *
   * Delegates to [[GZIPHeader$.detectOS]] for detection.
   *
   * @see
   *   [[GZIPHeader$.detectOS]]
   */
  def setOSAuto(): Unit = this.os = GZIPHeader.detectOS()

  /**
   * Sets the original file name, encoded as ISO-8859-1.
   *
   * @param name
   *   the original file name
   */
  def setName(name: String): Unit = {
    // ISO-8859-1 is always available on JVM; Scala.js/Native also support it.
    this.name = name.getBytes("ISO-8859-1")
  }

  /** Returns the original file name, or an empty string if not set. */
  def getName: String = {
    if (this.name == null) return ""
    new String(this.name, "ISO-8859-1")
  }

  /**
   * Sets a human-readable comment, encoded as ISO-8859-1.
   *
   * @param comment
   *   the comment text
   */
  def setComment(comment: String): Unit = {
    this.comment = comment.getBytes("ISO-8859-1")
  }

  /** Returns the comment, or an empty string if not set. */
  def getComment: String = {
    if (this.comment == null) return ""
    new String(this.comment, "ISO-8859-1")
  }

  /**
   * Sets the CRC-32 of the uncompressed data (stored in the GZIP trailer).
   *
   * @param crc
   *   the CRC-32 value
   */
  def setCRC(crc: Long): Unit = this.crc = crc

  /** Returns the CRC-32 value. */
  def getCRC: Long = crc

  private[jzlib] def put(d: Deflate): Unit = {
    var flag = 0
    if (text) flag |= 1             // FTEXT
    if (fhcrc) flag |= 2            // FHCRC
    if (extra != null) flag |= 4    // FEXTRA
    if (name != null) flag |= 8     // FNAME
    if (comment != null) flag |= 16 // FCOMMENT

    var xfl = 0
    if (d.level == JZlib.Z_BEST_SPEED) xfl |= 4
    else if (d.level == JZlib.Z_BEST_COMPRESSION) xfl |= 2

    d.put_short((0x8b1f & 0xffff).toShort) // ID1 ID2
    d.put_byte(8.toByte)                   // CM
    d.put_byte(flag.toByte)
    d.put_byte(mtime.toByte)
    d.put_byte((mtime >> 8).toByte)
    d.put_byte((mtime >> 16).toByte)
    d.put_byte((mtime >> 24).toByte)
    d.put_byte(xfl.toByte)
    d.put_byte(os.toByte)

    if (extra != null) {
      d.put_byte(extra.length.toByte)
      d.put_byte((extra.length >> 8).toByte)
      d.put_byte(extra, 0, extra.length)
    }
    if (name != null) {
      d.put_byte(name, 0, name.length)
      d.put_byte(0.toByte)
    }
    if (comment != null) {
      d.put_byte(comment, 0, comment.length)
      d.put_byte(0.toByte)
    }
  }

  override def clone(): GZIPHeader = {
    val gheader = super.clone().asInstanceOf[GZIPHeader]
    if (gheader.extra != null) {
      val tmp = new Array[Byte](gheader.extra.length)
      System.arraycopy(gheader.extra, 0, tmp, 0, tmp.length)
      gheader.extra = tmp
    }
    if (gheader.name != null) {
      val tmp = new Array[Byte](gheader.name.length)
      System.arraycopy(gheader.name, 0, tmp, 0, tmp.length)
      gheader.name = tmp
    }
    if (gheader.comment != null) {
      val tmp = new Array[Byte](gheader.comment.length)
      System.arraycopy(gheader.comment, 0, tmp, 0, tmp.length)
      gheader.comment = tmp
    }
    gheader
  }
}

/** OS identifier constants for [[GZIPHeader]] as defined in RFC 1952. */
object GZIPHeader {
  final val OS_MSDOS: Byte   = 0x00.toByte
  final val OS_AMIGA: Byte   = 0x01.toByte
  final val OS_VMS: Byte     = 0x02.toByte
  final val OS_UNIX: Byte    = 0x03.toByte
  final val OS_ATARI: Byte   = 0x05.toByte
  final val OS_OS2: Byte     = 0x06.toByte
  final val OS_MACOS: Byte   = 0x07.toByte
  final val OS_TOPS20: Byte  = 0x0a.toByte
  final val OS_WIN32: Byte   = 0x0b.toByte
  final val OS_VMCMS: Byte   = 0x04.toByte
  final val OS_ZSYSTEM: Byte = 0x08.toByte
  final val OS_CPM: Byte     = 0x09.toByte
  final val OS_QDOS: Byte    = 0x0c.toByte
  final val OS_RISCOS: Byte  = 0x0d.toByte
  final val OS_UNKNOWN: Byte = 0xff.toByte

  /**
   * Detects the current operating system and returns the corresponding GZIP OS constant.
   *
   * Uses `System.getProperty("os.name")` for detection:
   *   - Windows -> [[OS_MSDOS]] (0)
   *   - Linux, FreeBSD, SunOS -> [[OS_UNIX]] (3)
   *   - Mac OS X, Darwin -> [[OS_MACOS]] (7)
   *   - Other / null -> [[OS_UNKNOWN]] (255)
   *
   * @return
   *   the OS identifier byte as an `Int`
   */
  def detectOS(): Int = {
    val osName = System.getProperty("os.name")
    if (osName == null) return OS_UNKNOWN & 0xff
    val lower  = osName.toLowerCase
    if (lower.startsWith("windows")) OS_MSDOS & 0xff
    else if (
      lower.contains("linux") || lower.contains("nix") || lower.contains("nux") ||
      lower.contains("freebsd") || lower.contains("sunos")
    ) OS_UNIX & 0xff
    else if (lower.contains("mac") || lower.contains("darwin")) OS_MACOS & 0xff
    else OS_UNKNOWN & 0xff
  }
}
