package com.jcraft.jzlib.cli

import com.jcraft.jzlib._
import java.io._
import java.util.concurrent._

object PigzMain {

  private val Version = "scala-zlib pigz 1.0.0"

  case class Config(
    decompress: Boolean = false,
    processes: Int = Runtime.getRuntime.availableProcessors(),
    level: Int = 6,
    blockSize: Int = 131072,
    keep: Boolean = false,
    force: Boolean = false,
    stdout: Boolean = false,
    verbose: Boolean = false,
    recursive: Boolean = false,
    test: Boolean = false,
    list: Boolean = false,
    independent: Boolean = true,
    suffix: String = ".gz",
    files: List[String] = Nil,
  )

  def main(args: Array[String]): Unit = {
    val config =
      try {
        parseArgs(args)
      } catch {
        case e: Exception =>
          System.err.println(s"pigz: ${e.getMessage}")
          sys.exit(1)
      }

    var exitCode = 0

    if (config.files.isEmpty) {
      try {
        if (config.list) {
          System.err.println("pigz: --list does not work on stdin")
          exitCode = 1
        } else if (config.test) {
          decompressStream(System.in, NullOutputStream, config)
        } else if (config.decompress) {
          decompressStream(System.in, System.out, config)
        } else {
          compressParallel(System.in, System.out, config, 0L)
        }
        System.out.flush()
      } catch {
        case e: Exception =>
          System.err.println(s"pigz: ${e.getMessage}")
          exitCode = 1
      }
    } else {
      if (config.list) {
        System.out.println(
          f"${"compressed"}%10s ${"original"}%10s ${"ratio"}%6s  ${"name"}%s",
        )
      }

      config.files.foreach { path =>
        try {
          if (path == "-") {
            processStdin(config)
          } else {
            val file = new File(path)
            if (file.isDirectory) {
              if (config.recursive) {
                processDirectory(file, config)
              } else {
                System.err.println(s"pigz: $path is a directory -- skipping")
                exitCode = 1
              }
            } else {
              processFile(file, config)
            }
          }
        } catch {
          case e: Exception =>
            System.err.println(s"pigz: $path: ${e.getMessage}")
            exitCode = 1
        }
      }
    }

    sys.exit(exitCode)
  }

  private def processStdin(config: Config): Unit = {
    if (config.list) {
      System.err.println("pigz: --list does not work on stdin")
    } else if (config.test) {
      decompressStream(System.in, NullOutputStream, config)
    } else if (config.decompress) {
      decompressStream(System.in, System.out, config)
      System.out.flush()
    } else {
      compressParallel(System.in, System.out, config, 0L)
      System.out.flush()
    }
  }

  private def parseArgs(args: Array[String]): Config = {
    val expanded = expandArgs(args.toList)
    var config   = Config()
    var i        = 0
    while (i < expanded.length) {
      expanded(i) match {
        case "-h" | "--help"                                                                      =>
          printUsage()
          sys.exit(0)
        case "-V" | "--version"                                                                   =>
          System.err.println(Version)
          sys.exit(0)
        case "-d" | "--decompress" | "--uncompress"                                               =>
          config = config.copy(decompress = true)
        case "-k" | "--keep"                                                                      =>
          config = config.copy(keep = true)
        case "-f" | "--force"                                                                     =>
          config = config.copy(force = true)
        case "-c" | "--stdout" | "--to-stdout"                                                    =>
          config = config.copy(stdout = true)
        case "-v" | "--verbose"                                                                   =>
          config = config.copy(verbose = true)
        case "-r" | "--recursive"                                                                 =>
          config = config.copy(recursive = true)
        case "-t" | "--test"                                                                      =>
          config = config.copy(test = true, decompress = true)
        case "-l" | "--list"                                                                      =>
          config = config.copy(list = true, decompress = true)
        case "-i" | "--independent"                                                               =>
          config = config.copy(independent = true)
        case "--fast"                                                                             =>
          config = config.copy(level = 1)
        case "--best"                                                                             =>
          config = config.copy(level = 9)
        case "-p" | "--processes"                                                                 =>
          i += 1
          if (i >= expanded.length)
            throw new IllegalArgumentException("-p requires an argument")
          val n =
            try { expanded(i).toInt }
            catch {
              case _: NumberFormatException =>
                throw new IllegalArgumentException(
                  s"invalid number of processes: ${expanded(i)}",
                )
            }
          if (n < 1)
            throw new IllegalArgumentException(
              "number of processes must be at least 1",
            )
          config = config.copy(processes = n)
        case "-b" | "--blocksize"                                                                 =>
          i += 1
          if (i >= expanded.length)
            throw new IllegalArgumentException("-b requires an argument")
          val n =
            try { expanded(i).toInt }
            catch {
              case _: NumberFormatException =>
                throw new IllegalArgumentException(
                  s"invalid block size: ${expanded(i)}",
                )
            }
          if (n < 1)
            throw new IllegalArgumentException(
              "block size must be at least 1",
            )
          config = config.copy(blockSize = n)
        case "-S" | "--suffix"                                                                    =>
          i += 1
          if (i >= expanded.length)
            throw new IllegalArgumentException("-S requires an argument")
          config = config.copy(suffix = expanded(i))
        case s if s.length == 2 && s.charAt(0) == '-' && s.charAt(1) >= '0' && s.charAt(1) <= '9' =>
          config = config.copy(level = s.charAt(1) - '0')
        case "--"                                                                                 =>
          i += 1
          while (i < expanded.length) {
            config = config.copy(files = config.files :+ expanded(i))
            i += 1
          }
        case s if s.startsWith("-")                                                               =>
          throw new IllegalArgumentException(s"unknown option: $s")
        case file                                                                                 =>
          config = config.copy(files = config.files :+ file)
      }
      i += 1
    }
    config
  }

  /** Expands combined short options (e.g. -dk9 becomes -d -k -9, -p4 becomes -p 4). */
  private def expandArgs(args: List[String]): Array[String] = {
    val result         = new scala.collection.mutable.ListBuffer[String]()
    val argsWithValues = Set('p', 'b', 'S')

    args.foreach { arg =>
      if (arg.startsWith("--") || !arg.startsWith("-") || arg.length <= 2) {
        result += arg
      } else {
        val chars = arg.substring(1)
        var j     = 0
        while (j < chars.length) {
          val c = chars.charAt(j)
          if (argsWithValues.contains(c)) {
            result += s"-$c"
            val rest = chars.substring(j + 1)
            if (rest.nonEmpty) result += rest
            j = chars.length
          } else {
            result += s"-$c"
            j += 1
          }
        }
      }
    }
    result.toArray
  }

  private def processDirectory(dir: File, config: Config): Unit = {
    val files = dir.listFiles()
    if (files == null) {
      System.err.println(s"pigz: cannot read directory: ${dir.getPath}")
      return
    }
    files.sortBy(_.getName).foreach { f =>
      if (f.isDirectory) {
        processDirectory(f, config)
      } else {
        try {
          processFile(f, config)
        } catch {
          case e: Exception =>
            System.err.println(s"pigz: ${f.getPath}: ${e.getMessage}")
        }
      }
    }
  }

  private def processFile(file: File, config: Config): Unit = {
    if (!file.exists()) {
      throw new FileNotFoundException(
        s"${file.getPath}: No such file or directory",
      )
    }
    if (!file.isFile) {
      System.err.println(
        s"pigz: ${file.getPath}: not a regular file -- skipping",
      )
      return
    }

    if (config.list) {
      listFile(file, config)
    } else if (config.test) {
      testFile(file, config)
    } else if (config.decompress) {
      decompressFile(file, config)
    } else {
      compressFile(file, config)
    }
  }

  private def compressFile(file: File, config: Config): Unit = {
    val inputPath = file.getPath

    if (file.getName.endsWith(config.suffix) && !config.force) {
      System.err.println(
        s"pigz: $inputPath already has ${config.suffix} suffix -- skipping",
      )
      return
    }

    if (config.stdout) {
      val start = System.currentTimeMillis()
      val in    = new BufferedInputStream(new FileInputStream(file), 8192)
      try {
        val totalIn =
          compressParallel(in, System.out, config, file.lastModified() / 1000)
        System.out.flush()
        if (config.verbose) {
          val elapsed = System.currentTimeMillis() - start
          System.err.println(f"$inputPath: compressed $totalIn%d bytes in ${elapsed}%dms")
        }
      } finally {
        in.close()
      }
      return
    }

    val outputPath = inputPath + config.suffix
    val outputFile = new File(outputPath)

    if (outputFile.exists() && !config.force) {
      System.err.println(s"pigz: $outputPath already exists -- skipping")
      return
    }

    val start   = System.currentTimeMillis()
    val in      = new BufferedInputStream(new FileInputStream(file), 8192)
    val out     = new BufferedOutputStream(new FileOutputStream(outputFile), 8192)
    var success = false
    try {
      val totalIn =
        compressParallel(in, out, config, file.lastModified() / 1000)
      success = true
      if (config.verbose) {
        out.flush()
        val compressedSize = outputFile.length()
        val ratio          =
          if (totalIn > 0)
            f"${(1.0 - compressedSize.toDouble / totalIn) * 100}%.1f%%"
          else "0.0%"
        val elapsed        = System.currentTimeMillis() - start
        System.err.println(
          f"$inputPath: $ratio in ${elapsed}%dms ($totalIn%d => $compressedSize%d)",
        )
      }
    } finally {
      in.close()
      out.close()
      if (!success) outputFile.delete()
    }

    if (!config.keep) {
      if (!file.delete()) {
        System.err.println(s"pigz: cannot remove $inputPath")
      }
    }
  }

  private def decompressFile(file: File, config: Config): Unit = {
    val inputPath = file.getPath

    if (!inputPath.endsWith(config.suffix) && !config.force) {
      System.err.println(
        s"pigz: $inputPath: unknown suffix -- skipping",
      )
      return
    }

    if (config.stdout) {
      val in = new BufferedInputStream(new FileInputStream(file), 8192)
      try {
        decompressStream(in, System.out, config)
        System.out.flush()
      } finally {
        in.close()
      }
      return
    }

    val outputPath =
      if (inputPath.endsWith(config.suffix))
        inputPath.substring(0, inputPath.length - config.suffix.length)
      else inputPath + ".out"

    val outputFile = new File(outputPath)
    if (outputFile.exists() && !config.force) {
      System.err.println(s"pigz: $outputPath already exists -- skipping")
      return
    }

    val start   = System.currentTimeMillis()
    val in      = new BufferedInputStream(new FileInputStream(file), 8192)
    val out     = new BufferedOutputStream(new FileOutputStream(outputFile), 8192)
    var success = false
    try {
      decompressStream(in, out, config)
      success = true
      if (config.verbose) {
        val elapsed = System.currentTimeMillis() - start
        System.err.println(f"$inputPath: decompressed in ${elapsed}%dms")
      }
    } finally {
      in.close()
      out.close()
      if (!success) outputFile.delete()
    }

    if (!config.keep) {
      if (!file.delete()) {
        System.err.println(s"pigz: cannot remove $inputPath")
      }
    }
  }

  private def testFile(file: File, config: Config): Unit = {
    val start = System.currentTimeMillis()
    val in    = new BufferedInputStream(new FileInputStream(file), 8192)
    try {
      decompressStream(in, NullOutputStream, config)
      if (config.verbose) {
        val elapsed = System.currentTimeMillis() - start
        System.err.println(f"${file.getPath}: OK (${elapsed}%dms)")
      }
    } catch {
      case e: Exception =>
        throw new IOException(s"${file.getPath}: ${e.getMessage}")
    } finally {
      in.close()
    }
  }

  private def listFile(file: File, config: Config): Unit = {
    val compressedSize = file.length()
    val originalSize   = readGzipOriginalSize(file)

    var headerName: String = null
    try {
      val fis = new FileInputStream(file)
      try {
        val gis = new GZIPInputStream(fis)
        headerName = gis.getName()
        gis.close()
      } finally {
        fis.close()
      }
    } catch {
      case _: Exception => // ignore header read failures
    }

    val displayName =
      if (headerName != null && headerName.nonEmpty) headerName
      else {
        val name = file.getName
        if (name.endsWith(config.suffix))
          name.substring(0, name.length - config.suffix.length)
        else name
      }

    val ratio =
      if (originalSize > 0)
        f"${(1.0 - compressedSize.toDouble / originalSize) * 100}%.1f%%"
      else "0.0%"

    System.out.println(
      f"$compressedSize%10d $originalSize%10d $ratio%6s  $displayName%s",
    )
  }

  private def readGzipOriginalSize(file: File): Long = {
    val fileLen = file.length()
    if (fileLen < 18) return 0L

    val fis = new FileInputStream(file)
    try {
      var toSkip = fileLen - 4
      while (toSkip > 0) {
        val skipped = fis.skip(toSkip)
        if (skipped > 0) toSkip -= skipped
        else {
          if (fis.read() == -1) return 0L
          toSkip -= 1
        }
      }

      val b    = new Array[Byte](4)
      var read = 0
      while (read < 4) {
        val n = fis.read(b, read, 4 - read)
        if (n == -1) return 0L
        read += n
      }

      (b(0) & 0xffL) | ((b(1) & 0xffL) << 8) |
        ((b(2) & 0xffL) << 16) | ((b(3) & 0xffL) << 24)
    } finally {
      fis.close()
    }
  }

  /**
   * Compresses input to output using parallel independent GZIP members. Returns total bytes read from input.
   */
  private def compressParallel(
    input: InputStream,
    output: OutputStream,
    config: Config,
    mtime: Long,
  ): Long = {
    val executor   = Executors.newFixedThreadPool(config.processes)
    val maxPending = config.processes * 2
    val pending    = new java.util.ArrayDeque[Future[Array[Byte]]]()
    var totalIn    = 0L

    try {
      var eof = false
      while (!eof) {
        val buf       = new Array[Byte](config.blockSize)
        val bytesRead = readFully(input, buf)
        if (bytesRead == 0) {
          eof = true
        } else {
          totalIn += bytesRead
          val blockData =
            if (bytesRead == buf.length) buf
            else {
              val copy = new Array[Byte](bytesRead)
              System.arraycopy(buf, 0, copy, 0, bytesRead)
              copy
            }
          val lvl       = config.level
          val mt        = mtime

          pending.add(executor.submit(new Callable[Array[Byte]] {
            override def call(): Array[Byte] = compressBlock(blockData, lvl, mt)
          }))

          while (pending.size() >= maxPending) {
            output.write(pending.poll().get())
          }
        }
      }

      // Empty input produces a single empty GZIP member
      if (totalIn == 0) {
        output.write(compressBlock(new Array[Byte](0), config.level, mtime))
      }

      while (!pending.isEmpty) {
        output.write(pending.poll().get())
      }

      output.flush()
      totalIn
    } finally {
      executor.shutdown()
    }
  }

  /** Compresses a single data block as an independent GZIP member. */
  private def compressBlock(
    data: Array[Byte],
    level: Int,
    mtime: Long,
  ): Array[Byte] = {
    val baos     = new ByteArrayOutputStream(if (data.length > 0) data.length else 64)
    val deflater = new Deflater(level, 15 + 16)
    try {
      val gos = new GZIPOutputStream(baos, deflater, 512, false)
      if (mtime > 0) gos.setModifiedTime(mtime)
      if (data.length > 0) gos.write(data, 0, data.length)
      gos.close()
    } finally {
      deflater.end()
    }
    baos.toByteArray
  }

  /** Decompresses a GZIP stream (single-threaded). Handles concatenated members. */
  private def decompressStream(
    input: InputStream,
    output: OutputStream,
    config: Config,
  ): Unit = {
    val gis = new GZIPInputStream(input, 8192, false)
    try {
      val buf = new Array[Byte](config.blockSize)
      var n   = gis.read(buf)
      while (n != -1) {
        output.write(buf, 0, n)
        n = gis.read(buf)
      }
    } finally {
      gis.close()
    }
    output.flush()
  }

  /** Reads as many bytes as possible into buf. Returns number of bytes read (0 at EOF). */
  private def readFully(in: InputStream, buf: Array[Byte]): Int = {
    var offset = 0
    while (offset < buf.length) {
      val n = in.read(buf, offset, buf.length - offset)
      if (n == -1) return offset
      offset += n
    }
    offset
  }

  private object NullOutputStream extends OutputStream {
    override def write(b: Int): Unit                             = ()
    override def write(b: Array[Byte], off: Int, len: Int): Unit = ()
  }

  private def printUsage(): Unit = {
    val procs = Runtime.getRuntime.availableProcessors()
    System.out.println(
      s"""Usage: pigz [options] [files ...]
         |
         |Parallel GZIP compression utility (scala-zlib implementation).
         |
         |Options:
         |  -d, --decompress     Decompress (instead of compress)
         |  -k, --keep           Keep original file
         |  -f, --force          Force overwrite / compress files with suffix
         |  -c, --stdout         Write to stdout, keep original file
         |  -v, --verbose        Print compression statistics
         |  -r, --recursive      Process directories recursively
         |  -t, --test           Test compressed file integrity
         |  -l, --list           List compressed file contents
         |  -i, --independent    Each block is independent (default)
         |  -p N, --processes N  Compression threads (default: $procs)
         |  -b N, --blocksize N  Block size in bytes (default: 131072)
         |  -S .suf, --suffix .suf  Suffix (default: .gz)
         |  -1..-9               Compression level (default: 6)
         |  --fast               Same as -1
         |  --best               Same as -9
         |  -V, --version        Show version
         |  -h, --help           Show this help
         |
         |With no files, reads from stdin and writes to stdout.
         |Each block is compressed as an independent GZIP member (RFC 1952).""".stripMargin,
    )
  }
}
