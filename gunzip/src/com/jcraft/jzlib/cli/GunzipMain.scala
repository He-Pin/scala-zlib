package com.jcraft.jzlib.cli

import com.jcraft.jzlib._
import java.io._

object GunzipMain {
  private val Version = "1.1.5"

  case class Config(
    keep: Boolean = false,
    force: Boolean = false,
    recursive: Boolean = false,
    stdout: Boolean = false,
    verbose: Boolean = false,
    test: Boolean = false,
    list: Boolean = false,
    name: Boolean = true,
    quiet: Boolean = false,
    suffix: String = ".gz",
    files: List[String] = Nil,
  )

  def main(args: Array[String]): Unit = {
    val config = parseArgs(args.toList, Config())
    if (config == null) return

    if (config.files.isEmpty) {
      // Read from stdin, write to stdout
      decompressStream(System.in, System.out, config)
    } else {
      var exitCode = 0
      config.files.foreach { path =>
        val file = new File(path)
        if (!file.exists()) {
          if (!config.quiet)
            System.err.println(s"gunzip: $path: No such file or directory")
          exitCode = 1
        } else if (file.isDirectory) {
          if (config.recursive) {
            if (!processDirectory(file, config)) exitCode = 1
          } else {
            if (!config.quiet)
              System.err.println(s"gunzip: $path: Is a directory -- ignored")
            exitCode = 1
          }
        } else {
          if (!processFile(file, config)) exitCode = 1
        }
      }
      if (exitCode != 0) System.exit(exitCode)
    }
  }

  private def parseArgs(args: List[String], config: Config): Config = args match {
    case Nil                                                                           => config
    case ("-h" | "--help") :: _                                                        =>
      printHelp()
      null
    case ("-V" | "--version") :: _                                                     =>
      println(s"gunzip (scala-zlib) $Version")
      null
    case ("-k" | "--keep") :: rest                                                     =>
      parseArgs(rest, config.copy(keep = true))
    case ("-f" | "--force") :: rest                                                    =>
      parseArgs(rest, config.copy(force = true))
    case ("-r" | "--recursive") :: rest                                                =>
      parseArgs(rest, config.copy(recursive = true))
    case ("-c" | "--stdout" | "--to-stdout") :: rest                                   =>
      parseArgs(rest, config.copy(stdout = true, keep = true))
    case ("-v" | "--verbose") :: rest                                                  =>
      parseArgs(rest, config.copy(verbose = true))
    case ("-t" | "--test") :: rest                                                     =>
      parseArgs(rest, config.copy(test = true, keep = true))
    case ("-l" | "--list") :: rest                                                     =>
      parseArgs(rest, config.copy(list = true, keep = true))
    case ("-N" | "--name") :: rest                                                     =>
      parseArgs(rest, config.copy(name = true))
    case ("-n" | "--no-name") :: rest                                                  =>
      parseArgs(rest, config.copy(name = false))
    case ("-q" | "--quiet") :: rest                                                    =>
      parseArgs(rest, config.copy(quiet = true))
    case ("-S" | "--suffix") :: suffix :: rest                                         =>
      parseArgs(rest, config.copy(suffix = suffix))
    case "--" :: rest                                                                  =>
      config.copy(files = config.files ++ rest)
    case arg :: rest if arg.startsWith("-") && arg.length > 1 && !arg.startsWith("--") =>
      // Handle combined short options like -kvf
      val expanded = arg.substring(1).flatMap {
        case 'k' => List("-k")
        case 'f' => List("-f")
        case 'r' => List("-r")
        case 'c' => List("-c")
        case 'v' => List("-v")
        case 't' => List("-t")
        case 'l' => List("-l")
        case 'N' => List("-N")
        case 'n' => List("-n")
        case 'q' => List("-q")
        case 'h' => List("-h")
        case 'V' => List("-V")
        case 'S' => List("-S")
        case c   =>
          if (!config.quiet)
            System.err.println(s"gunzip: invalid option -- '$c'")
          List()
      }.toList
      parseArgs(expanded ++ rest, config)
    case file :: rest                                                                  =>
      parseArgs(rest, config.copy(files = config.files :+ file))
  }

  private def processFile(file: File, config: Config): Boolean = {
    if (config.list) {
      listFile(file)
      return true
    }
    if (config.test) return testFile(file, config)
    if (config.stdout) {
      decompressToStdout(file, config)
      return true
    }
    decompressFile(file, config)
  }

  private def processDirectory(dir: File, config: Config): Boolean = {
    val files = dir.listFiles()
    if (files == null) return true
    var ok    = true
    files.foreach { f =>
      if (f.isDirectory) {
        if (!processDirectory(f, config)) ok = false
      } else if (f.getName.endsWith(config.suffix)) {
        if (!processFile(f, config)) ok = false
      }
    }
    ok
  }

  private def decompressFile(file: File, config: Config): Boolean = {
    if (!file.getName.endsWith(config.suffix)) {
      if (!config.quiet)
        System.err.println(s"gunzip: ${file.getName}: unknown suffix -- ignored")
      return false
    }

    val outputName = if (config.name) {
      getOriginalName(file) match {
        case Some(n) if n.nonEmpty =>
          val parentPath = file.getParent
          if (parentPath != null) new File(parentPath, n).getPath else n
        case _                     =>
          file.getPath.stripSuffix(config.suffix)
      }
    } else {
      file.getPath.stripSuffix(config.suffix)
    }

    val outputFile = new File(outputName)
    if (outputFile.exists() && !config.force) {
      if (!config.quiet)
        System.err.println(s"gunzip: $outputName already exists; not overwritten (use -f to force)")
      return false
    }

    try {
      val compressedSize = file.length()
      val fis            = new FileInputStream(file)
      val gis            = new GZIPInputStream(fis)
      val fos            = new FileOutputStream(outputFile)
      try {
        val buf          = new Array[Byte](8192)
        var totalWritten = 0L
        var n            = gis.read(buf)
        while (n != -1) {
          fos.write(buf, 0, n)
          totalWritten += n
          n = gis.read(buf)
        }
        fos.flush()

        if (config.name) {
          val mtime = getModifiedTime(file)
          if (mtime > 0) outputFile.setLastModified(mtime * 1000)
        }

        if (config.verbose) {
          val ratio =
            if (totalWritten == 0) "0.0%"
            else f"${(1.0 - compressedSize.toDouble / totalWritten) * 100}%.1f%%"
          System.err.println(s"${file.getPath}:\t $ratio -- replaced with $outputName")
        }
      } finally {
        gis.close()
        fos.close()
      }
      if (!config.keep) file.delete()
      true
    } catch {
      case e: IOException =>
        if (!config.quiet)
          System.err.println(s"gunzip: ${file.getPath}: ${e.getMessage}")
        outputFile.delete()
        false
    }
  }

  private def decompressToStdout(file: File, config: Config): Boolean = {
    try {
      val fis = new FileInputStream(file)
      val gis = new GZIPInputStream(fis)
      try {
        decompressStream(gis, System.out, config)
      } finally {
        gis.close()
      }
      true
    } catch {
      case e: IOException =>
        if (!config.quiet)
          System.err.println(s"gunzip: ${file.getPath}: ${e.getMessage}")
        false
    }
  }

  private def decompressStream(input: InputStream, output: OutputStream, config: Config): Unit = {
    val gis = input match {
      case g: GZIPInputStream => g
      case _                  => new GZIPInputStream(input)
    }
    val buf = new Array[Byte](8192)
    var n   = gis.read(buf)
    while (n != -1) {
      output.write(buf, 0, n)
      n = gis.read(buf)
    }
    output.flush()
  }

  private def testFile(file: File, config: Config): Boolean = {
    try {
      val fis = new FileInputStream(file)
      val gis = new GZIPInputStream(fis)
      try {
        val buf = new Array[Byte](8192)
        var n   = gis.read(buf)
        while (n != -1) {
          n = gis.read(buf)
        }
      } finally {
        gis.close()
      }
      if (config.verbose) {
        System.err.println(s"${file.getPath}:\t OK")
      }
      true
    } catch {
      case e: Exception =>
        if (!config.quiet)
          System.err.println(s"${file.getPath}:\t FAILED (${e.getMessage})")
        false
    }
  }

  private def listFile(file: File): Unit = {
    try {
      val compressedSize = file.length()
      val raf            = new RandomAccessFile(file, "r")
      try {
        // Read GZIP header for original name and mtime
        val header    = new Array[Byte](10)
        val bytesRead = raf.read(header)
        if (
          bytesRead < 10 ||
          (header(0) & 0xff) != 0x1f ||
          (header(1) & 0xff) != 0x8b
        ) {
          System.err.println(s"gunzip: ${file.getPath}: not in gzip format")
          return
        }
        val flags     = header(3) & 0xff

        // Read uncompressed size from the last 4 bytes of the file
        if (compressedSize < 8L) {
          System.err.println(s"gunzip: ${file.getPath}: file too short")
          return
        }
        raf.seek(compressedSize - 4)
        val sizeBuf          = new Array[Byte](4)
        raf.readFully(sizeBuf)
        val uncompressedSize = readLittleEndianInt(sizeBuf, 0) & 0xffffffffL

        val ratio =
          if (uncompressedSize == 0) "0.0%"
          else f"${(1.0 - compressedSize.toDouble / uncompressedSize) * 100}%.1f%%"

        // Try to read the original name from the header
        val originalName = if ((flags & 0x08) != 0) { // FNAME flag
          raf.seek(10)
          // Skip FEXTRA if present
          if ((flags & 0x04) != 0) {
            val xlenBuf = new Array[Byte](2)
            raf.readFully(xlenBuf)
            val xlen    = (xlenBuf(0) & 0xff) | ((xlenBuf(1) & 0xff) << 8)
            raf.skipBytes(xlen)
          }
          // Read null-terminated name
          val nameBytes = new java.io.ByteArrayOutputStream()
          var b         = raf.read()
          while (b > 0) {
            nameBytes.write(b)
            b = raf.read()
          }
          new String(nameBytes.toByteArray, "ISO-8859-1")
        } else {
          file.getName.stripSuffix(".gz")
        }

        // Print header on first call
        println(f"${"compressed"}%20s ${"uncompressed"}%12s ${"ratio"}%7s ${"uncompressed_name"}%s")
        println(f"$compressedSize%20d $uncompressedSize%12d $ratio%7s $originalName%s")
      } finally {
        raf.close()
      }
    } catch {
      case e: IOException =>
        System.err.println(s"gunzip: ${file.getPath}: ${e.getMessage}")
    }
  }

  /** Reads the original filename from the GZIP header, if present. */
  private def getOriginalName(file: File): Option[String] = {
    try {
      val fis = new FileInputStream(file)
      try {
        val header = new Array[Byte](10)
        val n      = readFully(fis, header)
        if (
          n < 10 ||
          (header(0) & 0xff) != 0x1f ||
          (header(1) & 0xff) != 0x8b
        )
          return None
        val flags  = header(3) & 0xff
        if ((flags & 0x08) == 0) return None // No FNAME

        // Skip FEXTRA if present
        if ((flags & 0x04) != 0) {
          val xlenBuf = new Array[Byte](2)
          readFully(fis, xlenBuf)
          val xlen    = (xlenBuf(0) & 0xff) | ((xlenBuf(1) & 0xff) << 8)
          val skipped = fis.skip(xlen.toLong)
          if (skipped < xlen) {
            // Read remaining bytes if skip didn't skip enough
            val remaining = xlen - skipped.toInt
            val tmp       = new Array[Byte](remaining)
            readFully(fis, tmp)
          }
        }

        // Read null-terminated name
        val nameBytes = new java.io.ByteArrayOutputStream()
        var b         = fis.read()
        while (b > 0) {
          nameBytes.write(b)
          b = fis.read()
        }
        if (nameBytes.size() > 0) Some(new String(nameBytes.toByteArray, "ISO-8859-1"))
        else None
      } finally {
        fis.close()
      }
    } catch {
      case _: IOException => None
    }
  }

  /** Reads the modification time (seconds since epoch) from the GZIP header. */
  private def getModifiedTime(file: File): Long = {
    try {
      val fis = new FileInputStream(file)
      try {
        val header = new Array[Byte](10)
        val n      = readFully(fis, header)
        if (n < 10) return 0L
        readLittleEndianInt(header, 4) & 0xffffffffL
      } finally {
        fis.close()
      }
    } catch {
      case _: IOException => 0L
    }
  }

  private def readLittleEndianInt(buf: Array[Byte], off: Int): Int =
    (buf(off) & 0xff) |
      ((buf(off + 1) & 0xff) << 8) |
      ((buf(off + 2) & 0xff) << 16) |
      ((buf(off + 3) & 0xff) << 24)

  private def readFully(is: InputStream, buf: Array[Byte]): Int = {
    var off = 0
    val len = buf.length
    while (off < len) {
      val n = is.read(buf, off, len - off)
      if (n == -1) return off
      off += n
    }
    off
  }

  private def printHelp(): Unit = {
    println(s"""Usage: gunzip [OPTION]... [FILE]...
               |Decompress FILEs (by default, in-place).
               |
               |  -c, --stdout       write on standard output, keep original files unchanged
               |  -f, --force        force overwrite of output file
               |  -h, --help         give this help
               |  -k, --keep         keep (don't delete) input files
               |  -l, --list         list compressed file contents
               |  -n, --no-name      do not save or restore the original name and timestamp
               |  -N, --name         save or restore the original name and timestamp
               |  -q, --quiet        suppress all warnings
               |  -r, --recursive    operate recursively on directories
               |  -S, --suffix=SUF   use suffix SUF on compressed files (default: .gz)
               |  -t, --test         test compressed file integrity
               |  -v, --verbose      verbose mode
               |  -V, --version      display version number
               |
               |With no FILE, or when FILE is -, read standard input.
               |
               |Report bugs to https://github.com/He-Pin/scala-zlib/issues""".stripMargin)
  }
}
