# scala-zlib

[![Scala 2.13](https://img.shields.io/badge/Scala-2.13-red.svg)](https://www.scala-lang.org/)
[![Scala 3](https://img.shields.io/badge/Scala-3.3.7-red.svg)](https://www.scala-lang.org/)
[![License: BSD](https://img.shields.io/badge/License-BSD-blue.svg)](LICENSE.txt)
[![CI](https://github.com/scala-zlib/scala-zlib/actions/workflows/ci.yml/badge.svg)](https://github.com/scala-zlib/scala-zlib/actions)

**scala-zlib** is a pure Scala port of [jzlib](https://github.com/jruby/jzlib) — a Java re-implementation of zlib (RFC 1950/1951/1952). It provides deflate/inflate compression, GZIP support, streaming I/O wrappers, and checksums (Adler-32 and CRC-32) across all Scala platforms with no native dependencies.

## Why scala-zlib?

| Need | Solution |
|------|----------|
| Compression on Scala.js | ✅ Works — pure Scala algorithm, no JNI |
| Compression on Scala Native | ✅ Works — no system zlib dependency required |
| `Z_PARTIAL_FLUSH` / `Z_SYNC_FLUSH` | ✅ Full flush-mode support (unlike `java.util.zip`) |
| GZIP read/write | ✅ Built-in GZIPInputStream / GZIPOutputStream |
| Adler-32 / CRC-32 combining | ✅ `adler32_combine` / `crc32_combine` |
| Preset dictionary compression | ✅ Full zlib dictionary support |

## Upstream Project

scala-zlib is a direct Scala port of **jzlib**, originally authored by ymnk at JCraft, Inc. and currently maintained by the JRuby team at:

> https://github.com/jruby/jzlib

Each commit in this repository tracks one upstream jzlib commit. The upstream commit SHA is recorded in every commit message.

## Features

- **Deflate / Inflate**: all compression levels (0–9), all flush modes (`Z_NO_FLUSH`, `Z_PARTIAL_FLUSH`, `Z_SYNC_FLUSH`, `Z_FULL_FLUSH`, `Z_FINISH`)
- **GZIP format**: read and write RFC 1952 GZIP streams including headers
- **Streaming API**: `DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream` (JVM only)
- **Checksums**: Adler-32 and CRC-32, with combine operations
- **Preset dictionaries**: full support for zlib dictionary-based compression
- **Cross-platform**: JVM, Scala.js, Scala Native, WASM

## Installation

### Mill

```scala
// Core algorithm — JVM, JS, Native, WASM
def ivyDeps = Agg(ivy"com.github.scala-zlib::scala-zlib-core::1.1.5")

// JVM stream wrappers (includes core)
def ivyDeps = Agg(ivy"com.github.scala-zlib::scala-zlib-jvm::1.1.5")
```

### sbt

```scala
// Core algorithm — JVM, JS, Native, WASM
libraryDependencies += "com.github.scala-zlib" %%% "scala-zlib-core" % "1.1.5"

// JVM stream wrappers (includes core)
libraryDependencies += "com.github.scala-zlib" %% "scala-zlib-jvm" % "1.1.5"
```

## Quick Start

### Deflate and Inflate

```scala
import com.jcraft.jzlib._
import JZlib._

// Compress
val deflater = new Deflater
deflater.init(Z_DEFAULT_COMPRESSION)

val input  = "hello, hello!".getBytes
val output = new Array[Byte](1024)

deflater.setInput(input)
deflater.setOutput(output)
deflater.deflate(Z_FINISH)
val compressedLen = deflater.total_out.toInt
deflater.end()

// Decompress
val inflater = new Inflater
inflater.init()

val decompressed = new Array[Byte](1024)
inflater.setInput(output, 0, compressedLen)
inflater.setOutput(decompressed)
inflater.inflate(Z_NO_FLUSH)
val result = new String(decompressed, 0, inflater.total_out.toInt)
inflater.end()

println(result) // hello, hello!
```

### GZIP with Streams (JVM only)

```scala
import com.jcraft.jzlib._
import java.io._

// Write GZIP
val baos = new ByteArrayOutputStream
val gzOut = new GZIPOutputStream(baos)
gzOut.write("hello, world!".getBytes)
gzOut.finish()

// Read GZIP
val gzIn  = new GZIPInputStream(new ByteArrayInputStream(baos.toByteArray))
val bytes = gzIn.readAllBytes()
println(new String(bytes)) // hello, world!
```

### DeflaterOutputStream / InflaterInputStream (JVM only)

```scala
import com.jcraft.jzlib._
import java.io._

val buf    = new ByteArrayOutputStream
val defOut = new DeflaterOutputStream(buf)
defOut.write("hello, scala!".getBytes)
defOut.finish()

val infIn  = new InflaterInputStream(new ByteArrayInputStream(buf.toByteArray))
val result = new String(infIn.readAllBytes())
println(result) // hello, scala!
```

### Checksums

```scala
import com.jcraft.jzlib._

val adler = new Adler32
adler.update("hello".getBytes, 0, 5)
println(adler.getValue)   // Adler-32 checksum

val crc = new CRC32
crc.update("hello".getBytes, 0, 5)
println(crc.getValue)     // CRC-32 checksum

// Combine two independent checksums
val combined = JZlib.adler32_combine(adler1, adler2, len2)
```

### Preset Dictionary

```scala
import com.jcraft.jzlib._
import JZlib._

val dict = "hello, hello!".getBytes

val deflater = new Deflater
deflater.init(Z_DEFAULT_COMPRESSION)
deflater.setDictionary(dict, dict.length)
val dictId = deflater.getAdler

deflater.setInput("hello".getBytes)
deflater.setOutput(new Array[Byte](1024))
deflater.deflate(Z_FINISH)
deflater.end()

// Inflate with the same dictionary
val inflater = new Inflater
inflater.init()
// ... when inflate returns Z_NEED_DICT, supply the dictionary by dictId
```

## Platform Support

| Feature | JVM | Scala.js | Scala Native | WASM |
|---------|:---:|:--------:|:------------:|:----:|
| Deflate / Inflate | ✅ | ✅ | ✅ | ✅ |
| GZIP format | ✅ | ✅ | ✅ | ✅ |
| Adler-32 / CRC-32 | ✅ | ✅ | ✅ | ✅ |
| Preset dictionary | ✅ | ✅ | ✅ | ✅ |
| DeflaterOutputStream | ✅ | ❌ | ❌ | ❌ |
| InflaterInputStream | ✅ | ❌ | ❌ | ❌ |
| GZIPOutputStream | ✅ | ❌ | ❌ | ❌ |
| GZIPInputStream | ✅ | ❌ | ❌ | ❌ |
| ZOutputStream (legacy) | ✅ | ❌ | ❌ | ❌ |
| ZInputStream (legacy) | ✅ | ❌ | ❌ | ❌ |

Stream classes are in the `jvm` module and extend `java.io` streams. For JS/Native/WASM, use `Deflater` and `Inflater` directly from the `core` module and manage your own byte buffers.

## Cross-Platform Usage

On Scala.js, Scala Native, and WASM, use only the `core` module:

```scala
// Works on all platforms
import com.jcraft.jzlib._
import JZlib._

def compress(data: Array[Byte]): Array[Byte] = {
  val deflater = new Deflater
  deflater.init(Z_DEFAULT_COMPRESSION)
  val buf = new Array[Byte](data.length + 64)
  deflater.setInput(data)
  deflater.setOutput(buf)
  deflater.deflate(Z_FINISH)
  val n = deflater.total_out.toInt
  deflater.end()
  buf.take(n)
}

def decompress(data: Array[Byte], maxLen: Int = 65536): Array[Byte] = {
  val inflater = new Inflater
  inflater.init()
  val buf = new Array[Byte](maxLen)
  inflater.setInput(data)
  inflater.setOutput(buf)
  inflater.inflate(Z_NO_FLUSH)
  val n = inflater.total_out.toInt
  inflater.end()
  buf.take(n)
}
```

## API Overview

### `core` module — all platforms

| Class | Description |
|-------|-------------|
| `JZlib` | Constants (`Z_OK`, `Z_FINISH`, …), `WrapperType` enum, `adler32_combine`, `crc32_combine` |
| `Deflater` | High-level compression. Call `init`, `setInput`, `setOutput`, `deflate`, `end`. |
| `Inflater` | High-level decompression. Call `init`, `setInput`, `setOutput`, `inflate`, `end`. |
| `Adler32` | Adler-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `CRC32` | CRC-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `GZIPHeader` | GZIP header metadata (OS, filename, comment, modification time) |
| `GZIPException` | Thrown on GZIP format errors (extends `Exception`) |
| `ZStreamException` | Thrown on zlib stream errors (extends `Exception`) |
| `ZStream` | Low-level stream state — **deprecated**, use `Deflater`/`Inflater` instead |
| `Deflate` | Internal deflate algorithm (not part of public API) |
| `Inflate` | Internal inflate algorithm (not part of public API) |

### `jvm` module — JVM only

| Class | Description |
|-------|-------------|
| `DeflaterOutputStream` | Compressing `FilterOutputStream` |
| `InflaterInputStream` | Decompressing `FilterInputStream` |
| `GZIPOutputStream` | GZIP-format `DeflaterOutputStream` |
| `GZIPInputStream` | GZIP-format `InflaterInputStream` |
| `ZOutputStream` | Legacy compressing stream — **deprecated**, use `DeflaterOutputStream` |
| `ZInputStream` | Legacy decompressing stream — **deprecated**, use `InflaterInputStream` |

### `JZlib.WrapperType`

| Value | Meaning |
|-------|---------|
| `W_NONE` / `NONE` | Raw deflate (no header) |
| `W_ZLIB` / `ZLIB` | zlib wrapper (RFC 1950) |
| `W_GZIP` / `GZIP` | GZIP wrapper (RFC 1952) |
| `W_ANY` / `ANY` | Auto-detect on inflate |

## Development

### Prerequisites

- Java 17+
- [Mill](https://mill-build.com/) 0.11.12

### Commands

```bash
# Compile core for Scala 2.13
./mill core[2.13.16].compile

# Compile core for Scala 3
./mill core[3.3.7].compile

# Run tests (Scala 2.13)
./mill core[2.13.16].test

# Run all tests across all modules and Scala versions
./mill __.test

# Format all sources with scalafmt
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

# Check formatting without modifying files
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## License

BSD-style license — same as the original jzlib. See [LICENSE.txt](LICENSE.txt).

## Credits

- **jzlib** originally developed by [ymnk](https://github.com/ymnk) at [JCraft, Inc.](http://www.jcraft.com/jzlib/)
- Currently maintained by the [JRuby team](https://github.com/jruby/jzlib)
- Underlying algorithm by Jean-loup Gailly and Mark Adler ([zlib](https://zlib.net/))
- Scala port by the scala-zlib contributors
