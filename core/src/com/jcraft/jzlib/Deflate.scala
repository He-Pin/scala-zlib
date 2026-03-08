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

object Deflate {

  private[jzlib] class Config(
    val good_length: Int,
    val max_lazy:    Int,
    val nice_length: Int,
    val max_chain:   Int,
    val func:        Int
  )

  private final val MAX_MEM_LEVEL         = 9
  private final val Z_DEFAULT_COMPRESSION = -1
  private final val MAX_WBITS             = 15
  private final val DEF_MEM_LEVEL         = 8

  private final val STORED = 0
  private final val FAST   = 1
  private final val SLOW   = 2

  private[jzlib] val config_table: Array[Config] = {
    val t = new Array[Config](10)
    t(0) = new Config(0,    0,    0,    0, STORED)
    t(1) = new Config(4,    4,    8,    4, FAST)
    t(2) = new Config(4,    5,   16,    8, FAST)
    t(3) = new Config(4,    6,   32,   32, FAST)
    t(4) = new Config(4,    4,   16,   16, SLOW)
    t(5) = new Config(8,   16,   32,   32, SLOW)
    t(6) = new Config(8,   16,  128,  128, SLOW)
    t(7) = new Config(8,   32,  128,  256, SLOW)
    t(8) = new Config(32, 128,  258, 1024, SLOW)
    t(9) = new Config(32, 258,  258, 4096, SLOW)
    t
  }

  private[jzlib] val z_errmsg: Array[String] = Array(
    "need dictionary", "stream end", "", "file error", "stream error",
    "data error", "insufficient memory", "buffer error",
    "incompatible version", ""
  )

  private final val NeedMore      = 0
  private final val BlockDone     = 1
  private final val FinishStarted = 2
  private final val FinishDone    = 3

  private final val PRESET_DICT        = 0x20
  private final val Z_FILTERED         = 1
  private final val Z_HUFFMAN_ONLY     = 2
  private final val Z_RLE              = 3
  private final val Z_FIXED            = 4
  private final val Z_DEFAULT_STRATEGY = 0

  private final val Z_NO_FLUSH      = 0
  private final val Z_PARTIAL_FLUSH = 1
  private final val Z_SYNC_FLUSH    = 2
  private final val Z_FULL_FLUSH    = 3
  private final val Z_FINISH        = 4

  private final val Z_OK            =  0
  private final val Z_STREAM_END    =  1
  private final val Z_NEED_DICT     =  2
  private final val Z_ERRNO         = -1
  private final val Z_STREAM_ERROR  = -2
  private final val Z_DATA_ERROR    = -3
  private final val Z_MEM_ERROR     = -4
  private final val Z_BUF_ERROR     = -5
  private final val Z_VERSION_ERROR = -6

  private final val INIT_STATE   =  42
  private final val BUSY_STATE   = 113
  private final val FINISH_STATE = 666

  private final val Z_DEFLATED   = 8
  private final val STORED_BLOCK = 0
  private final val STATIC_TREES = 1
  private final val DYN_TREES    = 2

  private final val Z_BINARY  = 0
  private final val Z_ASCII   = 1
  private final val Z_UNKNOWN = 2

  private final val Buf_size      = 8 * 2
  private final val REP_3_6       = 16
  private final val REPZ_3_10     = 17
  private final val REPZ_11_138   = 18
  private final val MIN_MATCH     = 3
  private final val MAX_MATCH     = 258
  private final val MIN_LOOKAHEAD = MAX_MATCH + MIN_MATCH + 1
  private final val MAX_BITS      = 15
  private final val D_CODES       = 30
  private final val BL_CODES      = 19
  private final val LENGTH_CODES  = 29
  private final val LITERALS      = 256
  private final val L_CODES       = LITERALS + 1 + LENGTH_CODES
  private final val HEAP_SIZE     = 2 * L_CODES + 1
  private final val END_BLOCK     = 256

  @inline private[jzlib] def smaller(tree: Array[Short], n: Int, m: Int, depth: Array[Byte]): Boolean = {
    val tn2 = tree(n * 2)
    val tm2 = tree(m * 2)
    tn2 < tm2 || (tn2 == tm2 && depth(n) <= depth(m))
  }

  def deflateCopy(dest: ZStream, src: ZStream): Int = {
    if (src.dstate == null) return Z_STREAM_ERROR
    if (src.next_in != null) {
      dest.next_in = new Array[Byte](src.next_in.length)
      System.arraycopy(src.next_in, 0, dest.next_in, 0, src.next_in.length)
    }
    dest.next_in_index  = src.next_in_index
    dest.avail_in       = src.avail_in
    dest.total_in       = src.total_in
    if (src.next_out != null) {
      dest.next_out = new Array[Byte](src.next_out.length)
      System.arraycopy(src.next_out, 0, dest.next_out, 0, src.next_out.length)
    }
    dest.next_out_index = src.next_out_index
    dest.avail_out      = src.avail_out
    dest.total_out      = src.total_out
    dest.msg            = src.msg
    dest.data_type      = src.data_type
    dest.adler          = src.adler.copy()
    dest.dstate         = src.dstate.copy()
    dest.dstate.strm    = dest
    Z_OK
  }
}

private[jzlib] final class Deflate(var strm: ZStream) {
  import Deflate._

  var status:           Int         = 0
  var pending_buf:      Array[Byte] = null
  var pending_buf_size: Int         = 0
  var pending_out:      Int         = 0
  var pending:          Int         = 0
  var wrap:             Int         = 1
  var data_type:        Byte        = 0
  var method:           Byte        = 0
  var last_flush:       Int         = 0

  var w_size:      Int         = 0
  var w_bits:      Int         = 0
  var w_mask:      Int         = 0
  var window:      Array[Byte] = null
  var window_size: Int         = 0
  var prev:        Array[Short] = null
  var head:        Array[Short] = null

  var ins_h:      Int = 0
  var hash_size:  Int = 0
  var hash_bits:  Int = 0
  var hash_mask:  Int = 0
  var hash_shift: Int = 0

  var block_start:      Int = 0
  var match_length:     Int = 0
  var prev_match:       Int = 0
  var match_available:  Int = 0
  var strstart:         Int = 0
  var match_start:      Int = 0
  var lookahead:        Int = 0
  var prev_length:      Int = 0
  var max_chain_length: Int = 0
  var max_lazy_match:   Int = 0
  var level:            Int = 0
  var strategy:         Int = 0
  var good_match:       Int = 0
  var nice_match:       Int = 0

  var dyn_ltree: Array[Short] = new Array[Short](HEAP_SIZE * 2)
  var dyn_dtree: Array[Short] = new Array[Short]((2 * D_CODES + 1) * 2)
  var bl_tree:   Array[Short] = new Array[Short]((2 * BL_CODES + 1) * 2)

  var l_desc:  Tree = new Tree()
  var d_desc:  Tree = new Tree()
  var bl_desc: Tree = new Tree()

  var bl_count:  Array[Short] = new Array[Short](MAX_BITS + 1)
  var next_code: Array[Short] = new Array[Short](MAX_BITS + 1)
  var heap:      Array[Int]   = new Array[Int](2 * L_CODES + 1)
  var heap_len:  Int = 0
  var heap_max:  Int = 0
  var depth:     Array[Byte] = new Array[Byte](2 * L_CODES + 1)
  var sym_buf:    Array[Byte] = null
  var lit_bufsize:  Int = 0
  var last_lit:     Int = 0
  var sym_next:     Int = 0
  var sym_end:      Int = 0
  var opt_len:      Int = 0
  var static_len:   Int = 0
  var matches:      Int = 0
  var last_eob_len: Int = 0
  var bi_buf:   Short = 0
  var bi_valid: Int   = 0

  var gheader: GZIPHeader = null

  private[jzlib] def lm_init(): Unit = {
    window_size = 2 * w_size
    head(hash_size - 1) = 0
    var i = 0
    while (i < hash_size - 1) { head(i) = 0; i += 1 }
    max_lazy_match   = config_table(level).max_lazy
    good_match       = config_table(level).good_length
    nice_match       = config_table(level).nice_length
    max_chain_length = config_table(level).max_chain
    strstart         = 0
    block_start      = 0
    lookahead        = 0
    match_length     = MIN_MATCH - 1
    prev_length      = MIN_MATCH - 1
    match_available  = 0
    ins_h            = 0
  }

  private[jzlib] def tr_init(): Unit = {
    l_desc.dyn_tree   = dyn_ltree
    l_desc.stat_desc  = StaticTree.static_l_desc
    d_desc.dyn_tree   = dyn_dtree
    d_desc.stat_desc  = StaticTree.static_d_desc
    bl_desc.dyn_tree  = bl_tree
    bl_desc.stat_desc = StaticTree.static_bl_desc
    bi_buf       = 0
    bi_valid     = 0
    last_eob_len = 8
    init_block()
  }

  private[jzlib] def init_block(): Unit = {
    var i = 0
    while (i < L_CODES)  { dyn_ltree(i * 2) = 0; i += 1 }
    i = 0
    while (i < D_CODES)  { dyn_dtree(i * 2) = 0; i += 1 }
    i = 0
    while (i < BL_CODES) { bl_tree(i * 2) = 0; i += 1 }
    dyn_ltree(END_BLOCK * 2) = 1
    opt_len    = 0
    static_len = 0
    last_lit   = 0
    sym_next   = 0
    matches    = 0
  }

  private[jzlib] def pqdownheap(tree: Array[Short], k: Int): Unit = {
    val v = heap(k)
    var kk = k
    var j  = k << 1
    var loop = true
    while (j <= heap_len && loop) {
      if (j < heap_len && smaller(tree, heap(j + 1), heap(j), depth)) j += 1
      if (smaller(tree, v, heap(j), depth)) {
        loop = false
      } else {
        heap(kk) = heap(j); kk = j
        j <<= 1
      }
    }
    heap(kk) = v
  }

  private[jzlib] def scan_tree(tree: Array[Short], max_code: Int): Unit = {
    var n         = 0
    var prevlen   = -1
    var curlen    = 0
    var nextlen   = tree(0 * 2 + 1).toInt
    var count     = 0
    var max_count = 7
    var min_count = 4
    if (nextlen == 0) { max_count = 138; min_count = 3 }
    tree((max_code + 1) * 2 + 1) = 0xffff.toShort
    while (n <= max_code) {
      curlen = nextlen; nextlen = tree((n + 1) * 2 + 1)
      count += 1
      if (count < max_count && curlen == nextlen) {
        // continue: skip processing and reset
      } else {
        if (count < min_count) { bl_tree(curlen * 2) = (bl_tree(curlen * 2) + count).toShort }
        else if (curlen != 0) {
          if (curlen != prevlen) bl_tree(curlen * 2) = (bl_tree(curlen * 2) + 1).toShort
          bl_tree(REP_3_6 * 2) = (bl_tree(REP_3_6 * 2) + 1).toShort
        }
        else if (count <= 10) { bl_tree(REPZ_3_10 * 2) = (bl_tree(REPZ_3_10 * 2) + 1).toShort }
        else { bl_tree(REPZ_11_138 * 2) = (bl_tree(REPZ_11_138 * 2) + 1).toShort }
        count = 0; prevlen = curlen
        if (nextlen == 0)       { max_count = 138; min_count = 3 }
        else if (curlen == nextlen) { max_count = 6;   min_count = 3 }
        else                    { max_count = 7;   min_count = 4 }
      }
      n += 1
    }
  }

  private[jzlib] def build_bl_tree(): Int = {
    scan_tree(dyn_ltree, l_desc.max_code)
    scan_tree(dyn_dtree, d_desc.max_code)
    bl_desc.build_tree(this)
    var max_blindex = BL_CODES - 1
    while (max_blindex >= 3 && bl_tree((Tree.bl_order(max_blindex) & 0xff) * 2 + 1) == 0) {
      max_blindex -= 1
    }
    opt_len += 3 * (max_blindex + 1) + 5 + 5 + 4
    max_blindex
  }

  private[jzlib] def send_all_trees(lcodes: Int, dcodes: Int, blcodes: Int): Unit = {
    var rank = 0
    send_bits(lcodes - 257, 5)
    send_bits(dcodes - 1,   5)
    send_bits(blcodes - 4,  4)
    while (rank < blcodes) {
      send_bits(bl_tree((Tree.bl_order(rank) & 0xff) * 2 + 1), 3)
      rank += 1
    }
    send_tree(dyn_ltree, lcodes - 1)
    send_tree(dyn_dtree, dcodes - 1)
  }

  private[jzlib] def send_tree(tree: Array[Short], max_code: Int): Unit = {
    var n         = 0
    var prevlen   = -1
    var curlen    = 0
    var nextlen   = tree(0 * 2 + 1).toInt
    var count     = 0
    var max_count = 7
    var min_count = 4
    if (nextlen == 0) { max_count = 138; min_count = 3 }
    while (n <= max_code) {
      curlen = nextlen; nextlen = tree((n + 1) * 2 + 1)
      count += 1
      if (count < max_count && curlen == nextlen) {
        // continue: skip processing and reset
      } else if (count < min_count) {
        do { send_code(curlen, bl_tree); count -= 1 } while (count != 0)
        count = 0; prevlen = curlen
        if (nextlen == 0)           { max_count = 138; min_count = 3 }
        else if (curlen == nextlen) { max_count = 6;   min_count = 3 }
        else                        { max_count = 7;   min_count = 4 }
      } else if (curlen != 0) {
        if (curlen != prevlen) { send_code(curlen, bl_tree); count -= 1 }
        send_code(REP_3_6, bl_tree)
        send_bits(count - 3, 2)
        count = 0; prevlen = curlen
        if (nextlen == 0)           { max_count = 138; min_count = 3 }
        else if (curlen == nextlen) { max_count = 6;   min_count = 3 }
        else                        { max_count = 7;   min_count = 4 }
      } else if (count <= 10) {
        send_code(REPZ_3_10, bl_tree); send_bits(count - 3, 3)
        count = 0; prevlen = curlen
        if (nextlen == 0)           { max_count = 138; min_count = 3 }
        else if (curlen == nextlen) { max_count = 6;   min_count = 3 }
        else                        { max_count = 7;   min_count = 4 }
      } else {
        send_code(REPZ_11_138, bl_tree); send_bits(count - 11, 7)
        count = 0; prevlen = curlen
        if (nextlen == 0)           { max_count = 138; min_count = 3 }
        else if (curlen == nextlen) { max_count = 6;   min_count = 3 }
        else                        { max_count = 7;   min_count = 4 }
      }
      n += 1
    }
  }

  final private[jzlib] def put_byte(p: Array[Byte], start: Int, len: Int): Unit = {
    System.arraycopy(p, start, pending_buf, pending, len)
    pending += len
  }
  @inline final private[jzlib] def put_byte(c: Byte): Unit = { pending_buf(pending) = c; pending += 1 }
  @inline final private[jzlib] def put_short(w: Int): Unit = {
    put_byte(w.toByte)
    put_byte((w >>> 8).toByte)
  }
  final private[jzlib] def putShortMSB(b: Int): Unit = {
    put_byte((b >> 8).toByte)
    put_byte(b.toByte)
  }

  @inline final private[jzlib] def send_code(c: Int, tree: Array[Short]): Unit = {
    val c2 = c * 2
    send_bits(tree(c2) & 0xffff, tree(c2 + 1) & 0xffff)
  }

  private[jzlib] def send_bits(value: Int, length: Int): Unit = {
    val len = length
    if (bi_valid > Buf_size - len) {
      val v = value
      bi_buf = (bi_buf | ((v << bi_valid) & 0xffff)).toShort
      put_short(bi_buf)
      bi_buf = (v >>> (Buf_size - bi_valid)).toShort
      bi_valid += len - Buf_size
    } else {
      bi_buf = (bi_buf | ((value << bi_valid) & 0xffff)).toShort
      bi_valid += len
    }
  }

  private[jzlib] def _tr_align(): Unit = {
    send_bits(STATIC_TREES << 1, 3)
    send_code(END_BLOCK, StaticTree.static_ltree)
    bi_flush()
    if (1 + last_eob_len + 10 - bi_valid < 9) {
      send_bits(STATIC_TREES << 1, 3)
      send_code(END_BLOCK, StaticTree.static_ltree)
      bi_flush()
    }
    last_eob_len = 7
  }

  private[jzlib] def _tr_tally(dist: Int, lc: Int): Boolean = {
    sym_buf(sym_next) = (dist & 0xff).toByte; sym_next += 1
    sym_buf(sym_next) = ((dist >>> 8) & 0xff).toByte; sym_next += 1
    sym_buf(sym_next) = lc.toByte; sym_next += 1
    if (dist == 0) {
      dyn_ltree(lc * 2) = (dyn_ltree(lc * 2) + 1).toShort
    } else {
      matches += 1
      val d = dist - 1
      dyn_ltree(((Tree._length_code(lc) & 0xff) + LITERALS + 1) * 2) =
        (dyn_ltree(((Tree._length_code(lc) & 0xff) + LITERALS + 1) * 2) + 1).toShort
      dyn_dtree(Tree.d_code(d) * 2) = (dyn_dtree(Tree.d_code(d) * 2) + 1).toShort
    }
    if ((last_lit & 0x1fff) == 0 && level > 2) {
      var out_length = last_lit * 8
      val in_length  = strstart - block_start
      var dcode      = 0
      while (dcode < D_CODES) {
        out_length += (dyn_dtree(dcode * 2) & 0xffff) * (5 + Tree.extra_dbits(dcode))
        dcode += 1
      }
      out_length >>>= 3
      if (matches < (last_lit / 2) && out_length < in_length / 2) return true
    }
    sym_next == sym_end
  }

  private[jzlib] def compress_block(ltree: Array[Short], dtree: Array[Short]): Unit = {
    var dist  = 0
    var lc    = 0
    var lx    = 0
    var code  = 0
    var extra = 0
    if (sym_next != 0) {
      do {
        dist = (sym_buf(lx) & 0xff) | ((sym_buf(lx + 1) & 0xff) << 8)
        lc = sym_buf(lx + 2) & 0xff; lx += 3
        if (dist == 0) {
          send_code(lc, ltree)
        } else {
          code = Tree._length_code(lc) & 0xff
          send_code(code + LITERALS + 1, ltree)
          extra = Tree.extra_lbits(code)
          if (extra != 0) {
            val lcr = lc - Tree.base_length(code)
            send_bits(lcr, extra)
          }
          val dd = dist - 1
          code = Tree.d_code(dd)
          send_code(code, dtree)
          extra = Tree.extra_dbits(code)
          if (extra != 0) {
            val dr = dd - Tree.base_dist(code)
            send_bits(dr, extra)
          }
        }
      } while (lx < sym_next)
    }
    send_code(END_BLOCK, ltree)
    last_eob_len = ltree(END_BLOCK * 2 + 1)
  }

  private[jzlib] def set_data_type(): Unit = {
    var n          = 0
    var ascii_freq = 0
    var bin_freq   = 0
    while (n < 7)        { bin_freq   += dyn_ltree(n * 2); n += 1 }
    while (n < 128)      { ascii_freq += dyn_ltree(n * 2); n += 1 }
    while (n < LITERALS) { bin_freq   += dyn_ltree(n * 2); n += 1 }
    data_type = (if (bin_freq > (ascii_freq >>> 2)) Z_BINARY else Z_ASCII).toByte
  }

  @inline private[jzlib] def bi_flush(): Unit = {
    if (bi_valid == 16) {
      put_short(bi_buf)
      bi_buf   = 0
      bi_valid = 0
    } else if (bi_valid >= 8) {
      put_byte(bi_buf.toByte)
      bi_buf   = (bi_buf >>> 8).toShort
      bi_valid -= 8
    }
  }

  private[jzlib] def bi_windup(): Unit = {
    if (bi_valid > 8)      { put_short(bi_buf) }
    else if (bi_valid > 0) { put_byte(bi_buf.toByte) }
    bi_buf   = 0
    bi_valid = 0
  }

  private[jzlib] def copy_block(buf: Int, len: Int, header: Boolean): Unit = {
    bi_windup()
    last_eob_len = 8
    if (header) {
      put_short(len)
      put_short(~len)
    }
    put_byte(window, buf, len)
  }

  private[jzlib] def flush_block_only(eof: Boolean): Unit = {
    _tr_flush_block(if (block_start >= 0) block_start else -1, strstart - block_start, eof)
    block_start = strstart
    strm.flush_pending()
  }

  private[jzlib] def deflate_stored(flush: Int): Int = {
    var max_block_size = 0xffff
    if (max_block_size > pending_buf_size - 5) { max_block_size = pending_buf_size - 5 }
    var loop = true
    while (loop) {
      if (lookahead <= 1) {
        fill_window()
        if (lookahead == 0 && flush == Z_NO_FLUSH) return NeedMore
        if (lookahead == 0) { loop = false }
      }
      if (loop) {
        strstart += lookahead
        lookahead = 0
        val max_start = block_start + max_block_size
        if (strstart == 0 || strstart >= max_start) {
          lookahead = strstart - max_start
          strstart  = max_start
          flush_block_only(false)
          if (strm.avail_out == 0) return NeedMore
        }
        if (strstart - block_start >= w_size - MIN_LOOKAHEAD) {
          flush_block_only(false)
          if (strm.avail_out == 0) return NeedMore
        }
      }
    }
    flush_block_only(flush == Z_FINISH)
    if (strm.avail_out == 0)
      return if (flush == Z_FINISH) FinishStarted else NeedMore
    if (flush == Z_FINISH) FinishDone else BlockDone
  }

  private[jzlib] def _tr_stored_block(buf: Int, stored_len: Int, eof: Boolean): Unit = {
    send_bits((STORED_BLOCK << 1) + (if (eof) 1 else 0), 3)
    copy_block(buf, stored_len, true)
  }

  private[jzlib] def _tr_flush_block(buf: Int, stored_len: Int, eof: Boolean): Unit = {
    var opt_lenb    = 0
    var static_lenb = 0
    var max_blindex = 0
    if (level > 0) {
      if (data_type == Z_UNKNOWN) set_data_type()
      l_desc.build_tree(this)
      d_desc.build_tree(this)
      max_blindex = build_bl_tree()
      opt_lenb    = (opt_len + 3 + 7) >>> 3
      static_lenb = (static_len + 3 + 7) >>> 3
      if (static_lenb <= opt_lenb || strategy == Z_FIXED) opt_lenb = static_lenb
    } else {
      opt_lenb    = stored_len + 5
      static_lenb = stored_len + 5
    }
    if (stored_len + 4 <= opt_lenb && buf != -1) {
      _tr_stored_block(buf, stored_len, eof)
    } else if (static_lenb == opt_lenb) {
      send_bits((STATIC_TREES << 1) + (if (eof) 1 else 0), 3)
      compress_block(StaticTree.static_ltree, StaticTree.static_dtree)
    } else {
      send_bits((DYN_TREES << 1) + (if (eof) 1 else 0), 3)
      send_all_trees(l_desc.max_code + 1, d_desc.max_code + 1, max_blindex + 1)
      compress_block(dyn_ltree, dyn_dtree)
    }
    init_block()
    if (eof) { bi_windup() }
  }

  private[jzlib] def fill_window(): Unit = {
    var n    = 0
    var m    = 0
    var p    = 0
    var more = 0
    do {
      more = window_size - lookahead - strstart
      if (more == 0 && strstart == 0 && lookahead == 0) { more = w_size }
      else if (more == -1) { more -= 1 }
      else if (strstart >= w_size + w_size - MIN_LOOKAHEAD) {
        System.arraycopy(window, w_size, window, 0, w_size)
        match_start -= w_size
        strstart    -= w_size
        block_start -= w_size
        n = hash_size; p = n
        do {
          p -= 1
          m = head(p) & 0xffff
          head(p) = (if (m >= w_size) (m - w_size).toShort else 0)
          n -= 1
        } while (n != 0)
        n = w_size; p = n
        do {
          p -= 1
          m = prev(p) & 0xffff
          prev(p) = (if (m >= w_size) (m - w_size).toShort else 0)
          n -= 1
        } while (n != 0)
        more += w_size
      }
      if (strm.avail_in == 0) return
      n = strm.read_buf(window, strstart + lookahead, more)
      lookahead += n
      if (lookahead >= MIN_MATCH) {
        ins_h = window(strstart) & 0xff
        ins_h = (((ins_h) << hash_shift) ^ (window(strstart + 1) & 0xff)) & hash_mask
      }
    } while (lookahead < MIN_LOOKAHEAD && strm.avail_in != 0)
  }

  private[jzlib] def deflate_fast(flush: Int): Int = {
    var hash_head = 0
    var bflush    = false
    var loop      = true
    while (loop) {
      if (lookahead < MIN_LOOKAHEAD) {
        fill_window()
        if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NeedMore
        if (lookahead == 0) { loop = false }
      }
      if (loop) {
        if (lookahead >= MIN_MATCH) {
          ins_h = (((ins_h) << hash_shift) ^ (window(strstart + (MIN_MATCH - 1)) & 0xff)) & hash_mask
          hash_head = head(ins_h) & 0xffff
          prev(strstart & w_mask) = head(ins_h)
          head(ins_h) = strstart.toShort
        }
        if (hash_head != 0 && ((strstart - hash_head) & 0xffff) <= w_size - MIN_LOOKAHEAD) {
          if (strategy != Z_HUFFMAN_ONLY) { match_length = longest_match(hash_head) }
        }
        if (match_length >= MIN_MATCH) {
          bflush = _tr_tally(strstart - match_start, match_length - MIN_MATCH)
          lookahead -= match_length
          if (match_length <= max_lazy_match && lookahead >= MIN_MATCH) {
            match_length -= 1
            do {
              strstart += 1
              ins_h = ((ins_h << hash_shift) ^ (window(strstart + (MIN_MATCH - 1)) & 0xff)) & hash_mask
              hash_head = head(ins_h) & 0xffff
              prev(strstart & w_mask) = head(ins_h)
              head(ins_h) = strstart.toShort
              match_length -= 1
            } while (match_length != 0)
            strstart += 1
          } else {
            strstart     += match_length
            match_length  = 0
            ins_h         = window(strstart) & 0xff
            ins_h = (((ins_h) << hash_shift) ^ (window(strstart + 1) & 0xff)) & hash_mask
          }
        } else {
          bflush = _tr_tally(0, window(strstart) & 0xff)
          lookahead -= 1
          strstart  += 1
        }
        if (bflush) {
          flush_block_only(false)
          if (strm.avail_out == 0) return NeedMore
        }
      }
    }
    flush_block_only(flush == Z_FINISH)
    if (strm.avail_out == 0) {
      if (flush == Z_FINISH) return FinishStarted
      else return NeedMore
    }
    if (flush == Z_FINISH) FinishDone else BlockDone
  }

  private[jzlib] def deflate_slow(flush: Int): Int = {
    var hash_head = 0
    var bflush    = false
    var loop      = true
    while (loop) {
      if (lookahead < MIN_LOOKAHEAD) {
        fill_window()
        if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) return NeedMore
        if (lookahead == 0) { loop = false }
      }
      if (loop) {
        if (lookahead >= MIN_MATCH) {
          ins_h = (((ins_h) << hash_shift) ^ (window(strstart + (MIN_MATCH - 1)) & 0xff)) & hash_mask
          hash_head = head(ins_h) & 0xffff
          prev(strstart & w_mask) = head(ins_h)
          head(ins_h) = strstart.toShort
        }
        prev_length  = match_length
        prev_match   = match_start
        match_length = MIN_MATCH - 1
        if (hash_head != 0 && prev_length < max_lazy_match &&
            ((strstart - hash_head) & 0xffff) <= w_size - MIN_LOOKAHEAD) {
          if (strategy != Z_HUFFMAN_ONLY) { match_length = longest_match(hash_head) }
          if (match_length <= 5 && (strategy == Z_FILTERED ||
              (match_length == MIN_MATCH && strstart - match_start > 4096))) {
            match_length = MIN_MATCH - 1
          }
        }
        if (prev_length >= MIN_MATCH && match_length <= prev_length) {
          val max_insert = strstart + lookahead - MIN_MATCH
          bflush = _tr_tally(strstart - 1 - prev_match, prev_length - MIN_MATCH)
          lookahead    -= prev_length - 1
          prev_length  -= 2
          do {
            strstart += 1
            if (strstart <= max_insert) {
              ins_h = (((ins_h) << hash_shift) ^ (window(strstart + (MIN_MATCH - 1)) & 0xff)) & hash_mask
              hash_head = head(ins_h) & 0xffff
              prev(strstart & w_mask) = head(ins_h)
              head(ins_h) = strstart.toShort
            }
            prev_length -= 1
          } while (prev_length != 0)
          match_available = 0
          match_length    = MIN_MATCH - 1
          strstart       += 1
          if (bflush) {
            flush_block_only(false)
            if (strm.avail_out == 0) return NeedMore
          }
        } else if (match_available != 0) {
          bflush = _tr_tally(0, window(strstart - 1) & 0xff)
          if (bflush) { flush_block_only(false) }
          strstart  += 1
          lookahead -= 1
          if (strm.avail_out == 0) return NeedMore
        } else {
          match_available = 1
          strstart       += 1
          lookahead      -= 1
        }
      }
    }
    if (match_available != 0) {
      bflush = _tr_tally(0, window(strstart - 1) & 0xff)
      match_available = 0
    }
    flush_block_only(flush == Z_FINISH)
    if (strm.avail_out == 0) {
      if (flush == Z_FINISH) return FinishStarted
      else return NeedMore
    }
    if (flush == Z_FINISH) FinishDone else BlockDone
  }

  private[jzlib] def longest_match(cur_match: Int): Int = {
    var chain_length = max_chain_length
    var scan         = strstart
    var mtch         = 0
    var len          = 0
    var best_len     = prev_length
    val limit        = if (strstart > w_size - MIN_LOOKAHEAD) strstart - (w_size - MIN_LOOKAHEAD) else 0
    var nm           = nice_match
    val wmask        = w_mask
    val strend       = strstart + MAX_MATCH
    var scan_end1    = window(scan + best_len - 1)
    var scan_end     = window(scan + best_len)
    var cur          = cur_match
    if (prev_length >= good_match) { chain_length >>= 2 }
    if (nm > lookahead) nm = lookahead
    var break_outer  = false
    while (!break_outer) {
      mtch = cur
      if (window(mtch + best_len)     != scan_end  ||
          window(mtch + best_len - 1) != scan_end1 ||
          window(mtch)                != window(scan) ||
          { mtch += 1; window(mtch) } != window(scan + 1)) {
        // continue: skip to condition
      } else {
        scan += 2; mtch += 1
        do {} while (
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          { scan += 1; mtch += 1; window(scan) == window(mtch) } &&
          scan < strend
        )
        len  = MAX_MATCH - (strend - scan)
        scan = strend - MAX_MATCH
        if (len > best_len) {
          match_start = cur
          best_len    = len
          if (len >= nm) { break_outer = true }
          else {
            scan_end1 = window(scan + best_len - 1)
            scan_end  = window(scan + best_len)
          }
        }
      }
      if (!break_outer) {
        cur          = prev(cur & wmask) & 0xffff
        chain_length -= 1
        if (!(cur > limit && chain_length != 0)) break_outer = true
      }
    }
    if (best_len <= lookahead) best_len else lookahead
  }

  private[jzlib] def deflateInit(level: Int, bits: Int, memlevel: Int): Int =
    deflateInit(level, Z_DEFLATED, bits, memlevel, Z_DEFAULT_STRATEGY)

  private[jzlib] def deflateInit(level: Int, bits: Int): Int =
    deflateInit(level, Z_DEFLATED, bits, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY)

  private[jzlib] def deflateInit(level: Int): Int =
    deflateInit(level, MAX_WBITS)

  private def deflateInit(level: Int, method: Int, windowBits: Int, memLevel: Int, strategy: Int): Int = {
    var wrap        = 1
    var wb          = windowBits
    var lvl         = level
    strm.msg = null
    if (lvl == Z_DEFAULT_COMPRESSION) lvl = 6
    if (wb < 0) { wrap = 0; wb = -wb }
    else if (wb > 15) { wrap = 2; wb -= 16; strm.adler = new CRC32() }
    if (memLevel < 1 || memLevel > MAX_MEM_LEVEL || method != Z_DEFLATED ||
        wb < 8 || wb > 15 || lvl < 0 || lvl > 9 ||
        strategy < 0 || strategy > Z_HUFFMAN_ONLY ||
        (wb == 8 && wrap != 1)) {
      return Z_STREAM_ERROR
    }
    if (wb == 8) wb = 9 // upgrade 256-byte window to 512 (zlib wrapper signals this)
    strm.dstate       = this
    this.wrap         = wrap
    w_bits            = wb
    w_size            = 1 << w_bits
    w_mask            = w_size - 1
    hash_bits         = memLevel + 7
    hash_size         = 1 << hash_bits
    hash_mask         = hash_size - 1
    hash_shift        = (hash_bits + MIN_MATCH - 1) / MIN_MATCH
    window            = new Array[Byte](w_size * 2)
    prev              = new Array[Short](w_size)
    head              = new Array[Short](hash_size)
    lit_bufsize       = 1 << (memLevel + 6)
    pending_buf       = new Array[Byte](lit_bufsize * 4)
    pending_buf_size  = lit_bufsize * 4
    sym_buf           = new Array[Byte](lit_bufsize * 3)
    sym_end           = (lit_bufsize - 1) * 3
    this.level        = lvl
    this.strategy     = strategy
    this.method       = method.toByte
    deflateReset()
  }

  private[jzlib] def deflateReset(): Int = {
    strm.total_in  = 0
    strm.total_out = 0
    strm.msg       = null
    strm.data_type = Z_UNKNOWN
    pending        = 0
    pending_out    = 0
    if (wrap < 0) { wrap = -wrap }
    status     = if (wrap == 0) BUSY_STATE else INIT_STATE
    strm.adler.reset()
    last_flush = Z_NO_FLUSH
    tr_init()
    lm_init()
    Z_OK
  }

  private[jzlib] def deflateBound(sourceLen: Long): Long = {
    // upper bound for fixed blocks with 9-bit literals and length 255
    // (memLevel == 2, lowest that may not use stored blocks) -- ~13% overhead
    val fixedlen = sourceLen + (sourceLen >> 3) + (sourceLen >> 8) +
      (sourceLen >> 9) + 4

    // upper bound for stored blocks with length 127 (memLevel == 1) -- ~4% overhead
    val storelen = sourceLen + (sourceLen >> 5) + (sourceLen >> 7) +
      (sourceLen >> 11) + 7

    // compute wrapper length; use |wrap| to handle negative wrap after Z_STREAM_END
    val wrapAbs = if (wrap < 0) -wrap else wrap
    val wraplen: Long = wrapAbs match {
      case 0 => 0L // raw deflate
      case 1 =>    // zlib wrapper
        6L + (if (strstart != 0) 4L else 0L)
      case 2 => // gzip wrapper
        var wl = 18L
        if (gheader != null) {
          if (gheader.extra != null)
            wl += 2L + gheader.extra.length
          if (gheader.name != null)
            wl += gheader.name.length + 1L
          if (gheader.comment != null)
            wl += gheader.comment.length + 1L
          if (gheader.hcrc != 0)
            wl += 2L
        }
        wl
      case _ => 18L
    }

    // if not default parameters, return one of the conservative bounds
    if (w_bits != 15 || hash_bits != 8 + 7) {
      // level 0 always uses stored blocks, even when w_bits <= hash_bits
      (if (w_bits <= hash_bits && level != 0) fixedlen else storelen) + wraplen
    } else {
      // default settings: return tight bound -- ~0.03% overhead plus a small constant
      sourceLen + (sourceLen >> 12) + (sourceLen >> 14) +
        (sourceLen >> 25) + 13 - 6 + wraplen
    }
  }

  private[jzlib] def deflateEnd(): Int = {
    if (status != INIT_STATE && status != BUSY_STATE && status != FINISH_STATE) {
      return Z_STREAM_ERROR
    }
    pending_buf = null; sym_buf = null; head = null; prev = null; window = null
    if (status == BUSY_STATE) Z_DATA_ERROR else Z_OK
  }

  private[jzlib] def deflateParams(_level: Int, _strategy: Int): Int = {
    var err = Z_OK
    var lv  = _level
    if (lv == Z_DEFAULT_COMPRESSION) { lv = 6 }
    if (lv < 0 || lv > 9 || _strategy < 0 || _strategy > Z_HUFFMAN_ONLY) {
      return Z_STREAM_ERROR
    }
    if (config_table(level).func != config_table(lv).func && strm.total_in != 0) {
      err = strm.deflate(Z_PARTIAL_FLUSH)
    }
    if (level != lv) {
      level            = lv
      max_lazy_match   = config_table(level).max_lazy
      good_match       = config_table(level).good_length
      nice_match       = config_table(level).nice_length
      max_chain_length = config_table(level).max_chain
    }
    strategy = _strategy
    err
  }

  private[jzlib] def deflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    var length = dictLength
    var index  = 0
    if (dictionary == null || status != INIT_STATE) return Z_STREAM_ERROR
    strm.adler.update(dictionary, 0, dictLength)
    if (length < MIN_MATCH) return Z_OK
    if (length > w_size - MIN_LOOKAHEAD) { length = w_size - MIN_LOOKAHEAD; index = dictLength - length }
    System.arraycopy(dictionary, index, window, 0, length)
    strstart    = length
    block_start = length
    ins_h       = window(0) & 0xff
    ins_h = (((ins_h) << hash_shift) ^ (window(1) & 0xff)) & hash_mask
    var n = 0
    while (n <= length - MIN_MATCH) {
      ins_h = (((ins_h) << hash_shift) ^ (window(n + (MIN_MATCH - 1)) & 0xff)) & hash_mask
      prev(n & w_mask) = head(ins_h)
      head(ins_h) = n.toShort
      n += 1
    }
    Z_OK
  }

  private[jzlib] def deflateGetDictionary(dictionary: Array[Byte], dictLength: Array[Int]): Int = {
    if (status != INIT_STATE && status != BUSY_STATE && status != FINISH_STATE)
      return Z_STREAM_ERROR
    var len = strstart + lookahead
    if (len > w_size)
      len = w_size
    if (dictionary != null && len != 0)
      System.arraycopy(window, strstart + lookahead - len, dictionary, 0, len)
    if (dictLength != null)
      dictLength(0) = len
    Z_OK
  }

  private[jzlib] def deflate(flush: Int): Int = {
    var old_flush = 0
    if (flush > Z_FINISH || flush < 0) { return Z_STREAM_ERROR }
    if (strm.next_out == null || (strm.next_in == null && strm.avail_in != 0) ||
        (status == FINISH_STATE && flush != Z_FINISH)) {
      strm.msg = z_errmsg(Z_NEED_DICT - (Z_STREAM_ERROR))
      return Z_STREAM_ERROR
    }
    if (strm.avail_out == 0) {
      strm.msg = z_errmsg(Z_NEED_DICT - (Z_BUF_ERROR))
      return Z_BUF_ERROR
    }
    old_flush  = last_flush
    last_flush = flush
    if (status == INIT_STATE) {
      if (wrap == 2) {
        getGZIPHeader().put(this)
        status = BUSY_STATE
        strm.adler.reset()
      } else {
        var header = (Z_DEFLATED + ((w_bits - 8) << 4)) << 8
        var level_flags = ((level - 1) & 0xff) >> 1
        if (level_flags > 3) level_flags = 3
        header |= (level_flags << 6)
        if (strstart != 0) header |= PRESET_DICT
        header += 31 - (header % 31)
        status = BUSY_STATE
        putShortMSB(header)
        if (strstart != 0) {
          val adler = strm.adler.getValue
          putShortMSB((adler >>> 16).toInt)
          putShortMSB((adler & 0xffff).toInt)
        }
        strm.adler.reset()
      }
    }
    if (pending != 0) {
      strm.flush_pending()
      if (strm.avail_out == 0) {
        last_flush = -1
        return Z_OK
      }
    } else if (strm.avail_in == 0 && flush <= old_flush && flush != Z_FINISH) {
      strm.msg = z_errmsg(Z_NEED_DICT - (Z_BUF_ERROR))
      return Z_BUF_ERROR
    }
    if (status == FINISH_STATE && strm.avail_in != 0) {
      strm.msg = z_errmsg(Z_NEED_DICT - (Z_BUF_ERROR))
      return Z_BUF_ERROR
    }
    if (strm.avail_in != 0 || lookahead != 0 ||
        (flush != Z_NO_FLUSH && status != FINISH_STATE)) {
      var bstate = -1
      config_table(level).func match {
        case STORED => bstate = deflate_stored(flush)
        case FAST   => bstate = deflate_fast(flush)
        case SLOW   => bstate = deflate_slow(flush)
        case _      =>
      }
      if (bstate == FinishStarted || bstate == FinishDone) { status = FINISH_STATE }
      if (bstate == NeedMore || bstate == FinishStarted) {
        if (strm.avail_out == 0) { last_flush = -1 }
        return Z_OK
      }
      if (bstate == BlockDone) {
        if (flush == Z_PARTIAL_FLUSH) { _tr_align() }
        else {
          _tr_stored_block(0, 0, false)
          if (flush == Z_FULL_FLUSH) {
            var i = 0
            while (i < hash_size) { head(i) = 0; i += 1 }
          }
        }
        strm.flush_pending()
        if (strm.avail_out == 0) {
          last_flush = -1
          return Z_OK
        }
      }
    }
    if (flush != Z_FINISH) return Z_OK
    if (wrap <= 0) return Z_STREAM_END
    if (wrap == 2) {
      val adler = strm.adler.getValue
      put_byte((adler & 0xff).toByte)
      put_byte(((adler >> 8)  & 0xff).toByte)
      put_byte(((adler >> 16) & 0xff).toByte)
      put_byte(((adler >> 24) & 0xff).toByte)
      put_byte((strm.total_in & 0xff).toByte)
      put_byte(((strm.total_in >> 8)  & 0xff).toByte)
      put_byte(((strm.total_in >> 16) & 0xff).toByte)
      put_byte(((strm.total_in >> 24) & 0xff).toByte)
      getGZIPHeader().setCRC(adler)
    } else {
      val adler = strm.adler.getValue
      putShortMSB((adler >>> 16).toInt)
      putShortMSB((adler & 0xffff).toInt)
    }
    strm.flush_pending()
    if (wrap > 0) wrap = -wrap
    if (pending != 0) Z_OK else Z_STREAM_END
  }

  private[jzlib] def copy(): Deflate = {
    val dest = new Deflate(this.strm)
    dest.status           = this.status
    dest.pending_buf_size = this.pending_buf_size
    dest.pending_out      = this.pending_out
    dest.pending          = this.pending
    dest.wrap             = this.wrap
    dest.data_type        = this.data_type
    dest.method           = this.method
    dest.last_flush       = this.last_flush
    dest.w_size           = this.w_size
    dest.w_bits           = this.w_bits
    dest.w_mask           = this.w_mask
    dest.window_size      = this.window_size
    dest.ins_h            = this.ins_h
    dest.hash_size        = this.hash_size
    dest.hash_bits        = this.hash_bits
    dest.hash_mask        = this.hash_mask
    dest.hash_shift       = this.hash_shift
    dest.block_start      = this.block_start
    dest.match_length     = this.match_length
    dest.prev_match       = this.prev_match
    dest.match_available  = this.match_available
    dest.strstart         = this.strstart
    dest.match_start      = this.match_start
    dest.lookahead        = this.lookahead
    dest.prev_length      = this.prev_length
    dest.max_chain_length = this.max_chain_length
    dest.max_lazy_match   = this.max_lazy_match
    dest.level            = this.level
    dest.strategy         = this.strategy
    dest.good_match       = this.good_match
    dest.nice_match       = this.nice_match
    dest.heap_len         = this.heap_len
    dest.heap_max         = this.heap_max
    dest.lit_bufsize      = this.lit_bufsize
    dest.last_lit         = this.last_lit
    dest.sym_next         = this.sym_next
    dest.sym_end          = this.sym_end
    dest.opt_len          = this.opt_len
    dest.static_len       = this.static_len
    dest.matches          = this.matches
    dest.last_eob_len     = this.last_eob_len
    dest.bi_buf           = this.bi_buf
    dest.bi_valid         = this.bi_valid
    dest.pending_buf  = dup(this.pending_buf)
    dest.sym_buf      = dup(this.sym_buf)
    dest.window       = dup(this.window)
    dest.prev         = dup(this.prev)
    dest.head         = dup(this.head)
    dest.dyn_ltree    = dup(this.dyn_ltree)
    dest.dyn_dtree    = dup(this.dyn_dtree)
    dest.bl_tree      = dup(this.bl_tree)
    dest.bl_count     = dup(this.bl_count)
    dest.next_code    = dup(this.next_code)
    dest.heap         = dup(this.heap)
    dest.depth        = dup(this.depth)
    dest.l_desc.dyn_tree   = dest.dyn_ltree
    dest.l_desc.stat_desc  = this.l_desc.stat_desc
    dest.l_desc.max_code   = this.l_desc.max_code
    dest.d_desc.dyn_tree   = dest.dyn_dtree
    dest.d_desc.stat_desc  = this.d_desc.stat_desc
    dest.d_desc.max_code   = this.d_desc.max_code
    dest.bl_desc.dyn_tree  = dest.bl_tree
    dest.bl_desc.stat_desc = this.bl_desc.stat_desc
    dest.bl_desc.max_code  = this.bl_desc.max_code
    if (this.gheader != null) {
      dest.gheader = this.gheader.clone()
    }
    dest
  }

  private def dup(buf: Array[Byte]): Array[Byte] = {
    val foo = new Array[Byte](buf.length)
    System.arraycopy(buf, 0, foo, 0, foo.length)
    foo
  }
  private def dup(buf: Array[Short]): Array[Short] = {
    val foo = new Array[Short](buf.length)
    System.arraycopy(buf, 0, foo, 0, foo.length)
    foo
  }
  private def dup(buf: Array[Int]): Array[Int] = {
    val foo = new Array[Int](buf.length)
    System.arraycopy(buf, 0, foo, 0, foo.length)
    foo
  }

  private[jzlib] def getGZIPHeader(): GZIPHeader = synchronized {
    if (gheader == null) { gheader = new GZIPHeader() }
    gheader
  }
}
