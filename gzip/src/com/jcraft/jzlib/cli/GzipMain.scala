package com.jcraft.jzlib.cli

import com.jcraft.jzlib._
import java.io._

object GzipMain {

  private val Version    = "0.1.0"
  private val BufferSize = 8192

  case class Config(
    decompress: Boolean = false,
    stdout: Boolean = false,
    keep: Boolean = false,
    force: Boolean = false,
    recursive: Boolean = false,
    verbose: Boolean = false,
    test: Boolean = false,
    list: Boolean = false,
    level: Int = JZlib.Z_DEFAULT_COMPRESSION,
    suffix: String = ".gz",
    name: Boolean = true,
    files: List[String] = Nil,
  )

  def main(args: Array[String]): Unit = {
    val config = parseArgs(args.toList) match {
      case Some(c) => c
      case None    => sys.exit(1)
    }

    var exitCode = 0

    if (config.files.isEmpty) {
      if (config.list) {
        printListHeader()
      }
      try {
        if (config.decompress || config.test || config.list) {
          handleStdinDecompress(config)
        } else {
          if (!config.force && isTerminal(System.out)) {
            System.err.println(
              "gzip: compressed data not written to a terminal. Use -f to force.",
            )
            sys.exit(1)
          }
          handleStdinCompress(config)
        }
      } catch {
        case e: Exception =>
          System.err.println(s"gzip: ${e.getMessage}")
          exitCode = 1
      }
      if (config.list) {
        printListFooter(listTotalCompressed, listTotalUncompressed, 1)
      }
    } else {
      if (config.list) {
        printListHeader()
      }
      var fileCount = 0
      for (path <- config.files) {
        try {
          val file = new File(path)
          if (!file.exists()) {
            System.err.println(s"gzip: $path: No such file or directory")
            exitCode = 1
          } else if (file.isDirectory) {
            if (config.recursive) {
              val result = processDirectory(file, config)
              fileCount += result._1
              if (result._2 != 0) exitCode = 1
            } else {
              System.err.println(s"gzip: $path is a directory -- ignored")
              exitCode = 1
            }
          } else {
            processFile(file, config)
            fileCount += 1
          }
        } catch {
          case e: Exception =>
            System.err.println(s"gzip: $path: ${e.getMessage}")
            exitCode = 1
        }
      }
      if (config.list) {
        if (fileCount > 1) {
          printListFooter(listTotalCompressed, listTotalUncompressed, fileCount)
        }
      }
    }
    sys.exit(exitCode)
  }

  // --- Argument parsing ---

  private def parseArgs(args: List[String]): Option[Config] = {
    var config    = Config()
    var remaining = args

    while (remaining.nonEmpty) {
      remaining match {
        case "--decompress" :: rest                               =>
          config = config.copy(decompress = true); remaining = rest
        case "--uncompress" :: rest                               =>
          config = config.copy(decompress = true); remaining = rest
        case "--stdout" :: rest                                   =>
          config = config.copy(stdout = true, keep = true); remaining = rest
        case "--to-stdout" :: rest                                =>
          config = config.copy(stdout = true, keep = true); remaining = rest
        case "--keep" :: rest                                     =>
          config = config.copy(keep = true); remaining = rest
        case "--force" :: rest                                    =>
          config = config.copy(force = true); remaining = rest
        case "--recursive" :: rest                                =>
          config = config.copy(recursive = true); remaining = rest
        case "--verbose" :: rest                                  =>
          config = config.copy(verbose = true); remaining = rest
        case "--test" :: rest                                     =>
          config = config.copy(test = true, decompress = true)
          remaining = rest
        case "--list" :: rest                                     =>
          config = config.copy(list = true, decompress = true)
          remaining = rest
        case "--fast" :: rest                                     =>
          config = config.copy(level = 1); remaining = rest
        case "--best" :: rest                                     =>
          config = config.copy(level = 9); remaining = rest
        case "--no-name" :: rest                                  =>
          config = config.copy(name = false); remaining = rest
        case "--name" :: rest                                     =>
          config = config.copy(name = true); remaining = rest
        case "--version" :: _                                     =>
          printVersion(); sys.exit(0)
        case "--help" :: _                                        =>
          printHelp(); sys.exit(0)
        case "--suffix" :: s :: rest                              =>
          config = config.copy(suffix = if (s.startsWith(".")) s else "." + s)
          remaining = rest
        case "--suffix" :: Nil                                    =>
          System.err.println("gzip: --suffix requires an argument")
          return None
        case "--" :: rest                                         =>
          config = config.copy(files = config.files ++ rest)
          remaining = Nil
        case arg :: rest if arg.startsWith("--")                  =>
          System.err.println(s"gzip: unrecognized option '$arg'")
          return None
        case arg :: rest if arg.startsWith("-") && arg.length > 1 =>
          val result = parseShortFlags(arg.substring(1), config, rest)
          result match {
            case Some((c, r)) => config = c; remaining = r
            case None         => return None
          }
        case arg :: rest                                          =>
          config = config.copy(files = config.files :+ arg)
          remaining = rest
        case Nil                                                  => remaining = Nil
      }
    }

    Some(config)
  }

  private def parseShortFlags(
    flags: String,
    config: Config,
    rest: List[String],
  ): Option[(Config, List[String])] = {
    var c = config
    var r = rest
    var i = 0
    while (i < flags.length) {
      flags.charAt(i) match {
        case 'd'                          => c = c.copy(decompress = true)
        case 'c'                          => c = c.copy(stdout = true, keep = true)
        case 'k'                          => c = c.copy(keep = true)
        case 'f'                          => c = c.copy(force = true)
        case 'r'                          => c = c.copy(recursive = true)
        case 'v'                          => c = c.copy(verbose = true)
        case 't'                          => c = c.copy(test = true, decompress = true)
        case 'l'                          => c = c.copy(list = true, decompress = true)
        case 'n'                          => c = c.copy(name = false)
        case 'N'                          => c = c.copy(name = true)
        case 'V'                          => printVersion(); sys.exit(0)
        case 'h'                          => printHelp(); sys.exit(0)
        case 'q'                          => // quiet: accepted and ignored
        case 'S'                          =>
          val suffix = if (i + 1 < flags.length) {
            val s = flags.substring(i + 1)
            i = flags.length
            s
          } else {
            r match {
              case s :: rr => r = rr; s
              case Nil     =>
                System.err.println("gzip: -S requires an argument")
                return None
            }
          }
          c = c.copy(suffix = if (suffix.startsWith(".")) suffix else "." + suffix)
        case ch if ch >= '1' && ch <= '9' =>
          c = c.copy(level = ch - '0')
        case ch                           =>
          System.err.println(s"gzip: invalid option -- '$ch'")
          return None
      }
      i += 1
    }
    Some((c, r))
  }

  // --- Stdin/Stdout handling ---

  private def handleStdinCompress(config: Config): Unit = {
    val out      = new BufferedOutputStream(System.out, BufferSize)
    val deflater = new Deflater()
    deflater.init(resolveLevel(config.level), 15 + 16)
    val gzOut    = new GZIPOutputStream(out, deflater, BufferSize, false)
    gzOut.setModifiedTime(if (config.name) System.currentTimeMillis() / 1000 else 0)
    gzOut.setOS(GZIPHeader.detectOS())

    val buf     = new Array[Byte](BufferSize)
    var totalIn = 0L
    var n       = System.in.read(buf)
    while (n != -1) {
      gzOut.write(buf, 0, n)
      totalIn += n
      n = System.in.read(buf)
    }
    gzOut.finish()
    gzOut.flush()
    out.flush()

    if (config.verbose) {
      System.err.println(f"stdin:\t${totalIn}%d bytes compressed")
    }
  }

  private def handleStdinDecompress(config: Config): Unit = {
    val in   = new BufferedInputStream(System.in, BufferSize)
    val gzIn = new GZIPInputStream(in)
    val buf  = new Array[Byte](BufferSize)

    if (config.test) {
      var n = gzIn.read(buf)
      while (n != -1) {
        n = gzIn.read(buf)
      }
      gzIn.close()
      if (config.verbose) {
        System.err.println("stdin:\tOK")
      }
    } else if (config.list) {
      // Read through data to get sizes
      var totalOut = 0L
      var n        = gzIn.read(buf)
      while (n != -1) {
        totalOut += n
        n = gzIn.read(buf)
      }
      gzIn.close()
      printListEntry(-1, totalOut, "<stdin>")
    } else {
      val out = new BufferedOutputStream(System.out, BufferSize)
      var n   = gzIn.read(buf)
      while (n != -1) {
        out.write(buf, 0, n)
        n = gzIn.read(buf)
      }
      out.flush()
      gzIn.close()
    }
  }

  // --- File processing ---

  private def processFile(file: File, config: Config): Unit = {
    if (config.decompress || config.test || config.list) {
      decompressFile(file, config)
    } else {
      compressFile(file, config)
    }
  }

  private def processDirectory(
    dir: File,
    config: Config,
  ): (Int, Int) = {
    var count    = 0
    var errors   = 0
    val children = dir.listFiles()
    if (children != null) {
      for (child <- children) {
        try {
          if (child.isDirectory) {
            val result = processDirectory(child, config)
            count += result._1
            errors += result._2
          } else {
            processFile(child, config)
            count += 1
          }
        } catch {
          case e: Exception =>
            System.err.println(s"gzip: ${child.getPath}: ${e.getMessage}")
            errors += 1
        }
      }
    }
    (count, errors)
  }

  private def compressFile(file: File, config: Config): Unit = {
    val inputPath = file.getPath
    if (inputPath.endsWith(config.suffix) && !config.force) {
      System.err.println(
        s"gzip: $inputPath already has ${config.suffix} suffix -- unchanged",
      )
      return
    }

    val outputPath = inputPath + config.suffix
    val outputFile = new File(outputPath)

    if (!config.stdout && !config.force && outputFile.exists()) {
      System.err.println(s"gzip: $outputPath already exists; use -f to overwrite")
      return
    }

    val in = new BufferedInputStream(new FileInputStream(file), BufferSize)
    try {
      val out: OutputStream =
        if (config.stdout) new BufferedOutputStream(System.out, BufferSize)
        else new BufferedOutputStream(new FileOutputStream(outputFile), BufferSize)
      try {
        val deflater = new Deflater()
        deflater.init(resolveLevel(config.level), 15 + 16)
        val gzOut    = new GZIPOutputStream(out, deflater, BufferSize, false)

        val mtime = file.lastModified() / 1000
        gzOut.setModifiedTime(mtime)
        gzOut.setOS(GZIPHeader.detectOS())
        if (config.name) {
          gzOut.setName(file.getName)
        }

        val buf     = new Array[Byte](BufferSize)
        var totalIn = 0L
        var n       = in.read(buf)
        while (n != -1) {
          gzOut.write(buf, 0, n)
          totalIn += n
          n = in.read(buf)
        }
        gzOut.finish()
        gzOut.flush()
        out.flush()

        if (config.verbose && !config.stdout) {
          val totalOut = outputFile.length()
          printVerboseFile(inputPath, totalIn, totalOut)
        } else if (config.verbose) {
          printVerboseFile(inputPath, totalIn, -1)
        }
      } finally {
        if (!config.stdout) out.close()
        else out.flush()
      }
    } finally {
      in.close()
    }

    if (!config.stdout && !config.keep) {
      file.delete()
    }
  }

  private def decompressFile(file: File, config: Config): Unit = {
    val inputPath = file.getPath

    if (!inputPath.endsWith(config.suffix)) {
      System.err.println(s"gzip: $inputPath: unknown suffix -- ignored")
      return
    }

    val outputPath =
      inputPath.substring(0, inputPath.length - config.suffix.length)
    val outputFile = new File(outputPath)

    if (
      !config.stdout && !config.test && !config.list &&
      !config.force && outputFile.exists()
    ) {
      System.err.println(s"gzip: $outputPath already exists; use -f to overwrite")
      return
    }

    val in = new BufferedInputStream(new FileInputStream(file), BufferSize)
    try {
      val gzIn = new GZIPInputStream(in)
      try {
        if (config.list) {
          var totalOut       = 0L
          val buf            = new Array[Byte](BufferSize)
          var n              = gzIn.read(buf)
          while (n != -1) {
            totalOut += n
            n = gzIn.read(buf)
          }
          val compressedSize = file.length()
          printListEntry(compressedSize, totalOut, inputPath)
        } else if (config.test) {
          val buf = new Array[Byte](BufferSize)
          var n   = gzIn.read(buf)
          while (n != -1) {
            n = gzIn.read(buf)
          }
          if (config.verbose) {
            System.err.println(s"$inputPath:\tOK")
          }
        } else {
          val out: OutputStream =
            if (config.stdout) new BufferedOutputStream(System.out, BufferSize)
            else
              new BufferedOutputStream(new FileOutputStream(outputFile), BufferSize)
          try {
            val buf      = new Array[Byte](BufferSize)
            var totalOut = 0L
            var n        = gzIn.read(buf)
            while (n != -1) {
              out.write(buf, 0, n)
              totalOut += n
              n = gzIn.read(buf)
            }
            out.flush()

            if (config.verbose) {
              val totalIn = file.length()
              printVerboseFile(inputPath, totalOut, totalIn)
            }
          } finally {
            if (!config.stdout) out.close()
            else out.flush()
          }
        }
      } finally {
        gzIn.close()
      }
    } finally {
      in.close()
    }

    if (!config.stdout && !config.test && !config.list && !config.keep) {
      file.delete()
    }
  }

  // --- List mode ---

  private var listTotalCompressed   = 0L
  private var listTotalUncompressed = 0L

  private def printListHeader(): Unit = {
    System.out.println(
      f"${"compressed"}%20s ${"uncompressed"}%20s ${"ratio"}%7s ${"uncompressed_name"}%s",
    )
  }

  private def printListEntry(
    compressed: Long,
    uncompressed: Long,
    name: String,
  ): Unit = {
    listTotalCompressed += compressed
    listTotalUncompressed += uncompressed
    val ratio   =
      if (uncompressed == 0) "0.0%"
      else f"${(1.0 - compressed.toDouble / uncompressed.toDouble) * 100.0}%.1f%%"
    val compStr = if (compressed < 0) "-" else compressed.toString
    System.out.println(f"$compStr%20s $uncompressed%20d $ratio%7s $name%s")
  }

  private def printListFooter(
    totalCompressed: Long,
    totalUncompressed: Long,
    fileCount: Int,
  ): Unit = {
    val ratio =
      if (totalUncompressed == 0) "0.0%"
      else
        f"${(1.0 - totalCompressed.toDouble / totalUncompressed.toDouble) * 100.0}%.1f%%"
    System.out.println(
      f"$totalCompressed%20d $totalUncompressed%20d $ratio%7s (totals)",
    )
  }

  // --- Verbose output ---

  private def printVerboseFile(
    name: String,
    originalSize: Long,
    compressedSize: Long,
  ): Unit = {
    if (compressedSize < 0) {
      System.err.println(s"$name:")
    } else {
      val ratio =
        if (originalSize == 0) "0.0%"
        else
          f"${(1.0 - compressedSize.toDouble / originalSize.toDouble) * 100.0}%.1f%%"
      System.err.println(f"$name:\t $ratio%s")
    }
  }

  // --- Utility ---

  private def resolveLevel(level: Int): Int = {
    if (level == JZlib.Z_DEFAULT_COMPRESSION) 6
    else level
  }

  private def isTerminal(out: OutputStream): Boolean = {
    // Heuristic: System.console() is non-null when stdout is a terminal
    try {
      System.console() != null
    } catch {
      case _: Exception => false
    }
  }

  // --- Help and version ---

  private def printVersion(): Unit = {
    System.out.println(s"scala-zlib gzip $Version")
    System.out.println("Copyright (c) JCraft, Inc.")
    System.out.println("Pure Scala implementation of gzip using com.jcraft.jzlib")
  }

  private def printHelp(): Unit = {
    System.out.println("Usage: gzip [OPTION]... [FILE]...")
    System.out.println("Compress or uncompress FILEs (by default, compress in place).")
    System.out.println()
    System.out.println("  -c, --stdout      write on standard output, keep original files")
    System.out.println("  -d, --decompress  decompress")
    System.out.println("  -f, --force       force overwrite of output file")
    System.out.println("  -h, --help        give this help")
    System.out.println("  -k, --keep        keep (don't delete) input files")
    System.out.println("  -l, --list        list compressed file contents")
    System.out.println("  -n, --no-name     do not save or restore the original name and timestamp")
    System.out.println("  -N, --name        save or restore the original name and timestamp")
    System.out.println("  -r, --recursive   operate recursively on directories")
    System.out.println(
      "  -S, --suffix=SUF  use suffix SUF on compressed files (default: .gz)",
    )
    System.out.println("  -t, --test        test compressed file integrity")
    System.out.println("  -v, --verbose     verbose mode")
    System.out.println("  -V, --version     display version number")
    System.out.println("  -1, --fast        compress faster")
    System.out.println("  -9, --best        compress better")
    System.out.println()
    System.out.println("With no FILE, or when FILE is -, read standard input.")
    System.out.println()
    System.out.println(
      "Report bugs to https://github.com/jcraft-inc/scala-zlib/issues",
    )
  }
}
