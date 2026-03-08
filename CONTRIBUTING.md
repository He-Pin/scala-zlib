# Contributing to scala-zlib

Thank you for your interest in contributing to scala-zlib! This guide covers everything you need to get started, from setting up your development environment to submitting a pull request.

## Prerequisites

- **Java 17+** (check with `java -version`)
- **Mill 0.11.12** (see [Getting Started](#getting-started))
- **Git**
- Basic familiarity with Scala and zlib concepts

## Getting Started

### 1. Fork and clone

```bash
git clone https://github.com/<your-username>/scala-zlib.git
cd scala-zlib
```

### 2. Install Mill

```bash
curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.12/0.11.12 > mill
chmod +x mill
```

Alternatively, use [coursier](https://get-coursier.io/) or your OS package manager.

### 3. Verify setup

```bash
./mill core[2.13.16].compile
```

You should see a successful compilation. If Mill downloads dependencies for a while on the first run, that is normal.

## Project Structure

```
scala-zlib/
├── build.sc                    # Mill build definition (modules, deps, cross-versions)
├── .scalafmt.conf              # Scalafmt code-formatting configuration
├── .mill-version               # Pins the Mill version used
├── README.md                   # Project README
├── CONTRIBUTING.md             # This file
├── CLAUDE.md                   # AI-assistant guide
├── LICENSE.txt                 # BSD-style license
├── ChangeLog                   # Original jzlib changelog
├── core/                       # Cross-platform algorithm module (JVM + JS + Native + WASM)
│   └── src/
│       └── com/jcraft/jzlib/
│           ├── JZlib.scala     # Constants and WrapperType enum
│           ├── ZStream.scala   # Low-level stream state (deprecated)
│           ├── Deflater.scala  # High-level compression
│           ├── Inflater.scala  # High-level decompression
│           ├── Deflate.scala   # Internal deflate algorithm
│           ├── Inflate.scala   # Internal inflate algorithm
│           ├── InfBlocks.scala # Inflate block decoder
│           ├── InfCodes.scala  # Inflate code decoder
│           ├── InfTree.scala   # Inflate Huffman tree builder
│           ├── Tree.scala      # Huffman tree
│           ├── StaticTree.scala# Static Huffman trees
│           ├── Adler32.scala   # Adler-32 checksum
│           ├── CRC32.scala     # CRC-32 checksum
│           ├── GZIPHeader.scala# GZIP header data
│           ├── GZIPException.scala     # Exception (extends Exception, NOT IOException)
│           └── ZStreamException.scala  # Exception (extends Exception, NOT IOException)
├── jvm/                        # JVM-only stream wrappers
│   └── src/
│       └── com/jcraft/jzlib/
│           ├── DeflaterOutputStream.scala
│           ├── InflaterInputStream.scala
│           ├── GZIPOutputStream.scala
│           ├── GZIPInputStream.scala
│           ├── ZOutputStream.scala     # Deprecated legacy stream
│           └── ZInputStream.scala      # Deprecated legacy stream
└── .github/
    └── workflows/
        └── ci.yml              # GitHub Actions CI configuration
```

## Development Workflow

### 1. Create a feature branch

```bash
git checkout -b my-feature
```

### 2. Make your changes

Keep changes focused. One logical change per commit.

### 3. Run the tests

```bash
# Test a single module
./mill core[2.13.16].test
./mill jvm[2.13.16].test

# Test all modules and Scala versions
./mill __.test

# Run a specific test suite
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite
```

### 4. Format your code

```bash
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
```

### 5. Verify formatting is clean

```bash
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

CI will fail if this check fails. Always run it before pushing.

### 6. Write a commit message (see format below)

### 7. Push and open a pull request

```bash
git push origin my-feature
```

Then open a pull request on GitHub. Describe the motivation, what changed, and link to any related issues or upstream commits.

## Commit Message Format

Every commit must follow this format:

```
Short title (50 chars max)

Motivation:
Why this change was made — what problem it solves or what improvement it brings.

Modification:
What was changed — files, classes, methods, logic.

Result:
The observable effect or outcome of this change.

References:
- https://github.com/jruby/jzlib/commit/XXXXXXXX  (upstream jzlib commit, if applicable)
- Fixes #42 (GitHub issue, if applicable)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

### Example

```
Port Adler32.combine from jzlib

Motivation:
The combine operation allows merging two independently computed Adler-32
checksums, which is needed for parallel compression.

Modification:
Added Adler32.combine(adler1: Long, adler2: Long, len2: Long): Long
in core/src/com/jcraft/jzlib/Adler32.scala.

Result:
Users can now combine Adler-32 values without recomputing from scratch.
JZlib.adler32_combine delegates to this method.

References:
- https://github.com/jruby/jzlib/commit/6900f5

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Migrating Upstream jzlib Commits

scala-zlib tracks jzlib one commit at a time. When porting an upstream commit:

1. **Find the upstream commit**
   ```
   https://github.com/jruby/jzlib/commit/<SHA>
   ```

2. **Identify which files changed** and locate the corresponding Scala sources in `core/` or `jvm/`.

3. **Translate Java to idiomatic Scala**
   - Use `val` / `var` appropriately (algorithm state is `var`)
   - Replace Java arrays `int[]` → `Array[Int]`, `byte[]` → `Array[Byte]`
   - Preserve the imperative style in algorithm code (see [Code Style](#code-style))
   - Replace `throws IOException` with Scala's unchecked exceptions

4. **Update tests to munit format** (see [Testing](#testing))

5. **Run scalafmt**
   ```bash
   ./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
   ```

6. **Run tests**
   ```bash
   ./mill __.test
   ```

7. **Commit with the upstream SHA** in the References section.

## Code Style

### General

- Follow the `.scalafmt.conf` configuration — always run scalafmt before committing.
- Keep the public API close to jzlib for compatibility with existing code that uses jzlib.
- Use `Array[Byte]` and `Array[Int]` (not `List`, `Vector`, `Seq`) for performance-critical data paths.
- Keep the imperative, mutable style in algorithm files (`Deflate`, `Inflate`, `InfBlocks`, `InfCodes`, `InfTree`, `Tree`). These are direct ports of C/Java low-level algorithms; readability comes from faithfulness to the original, not from functional idioms.

### ScalaDoc

Add ScalaDoc comments to all public API methods and classes:

```scala
/** Compresses data using the deflate algorithm.
  *
  * @param flush one of [[JZlib.Z_NO_FLUSH]], [[JZlib.Z_SYNC_FLUSH]], [[JZlib.Z_FINISH]], etc.
  * @return [[JZlib.Z_OK]], [[JZlib.Z_STREAM_END]], or an error code
  */
def deflate(flush: Int): Int = ...
```

### Cross-Platform Constraints

The `core` module must compile on JVM, Scala.js, Scala Native, and WASM. This means:

**Do NOT use in `core`:**
- `java.io.*` — use the `jvm` module for any stream classes
- `java.net.*`
- `java.util.zip.*`
- Any reflection or JVM-specific API

**Safe to use in `core`:**
- `java.lang.*` (including `System.arraycopy`, `Math.*`, `Integer.*`)
- `java.nio.charset.StandardCharsets`
- Scala standard library (`scala.*`)

Stream classes (`DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream`, `ZOutputStream`, `ZInputStream`) belong in the `jvm` module only.

### Exception Hierarchy

In the Scala port, exceptions are cross-platform and must **not** extend `java.io.IOException`:

```scala
// ✅ Correct — extends Exception for cross-platform compatibility
class GZIPException(msg: String) extends Exception(msg)
class ZStreamException(msg: String) extends Exception(msg)
```

JVM stream classes in the `jvm` module may catch/rethrow as `java.io.IOException` at the boundary.

## Testing

### Framework

Tests use [munit](https://scalameta.org/munit/), which is cross-platform (JVM + JS + Native):

```scala
class Adler32Suite extends munit.FunSuite {

  test("compatible with java.util.zip.Adler32") {
    val data = Array.fill[Byte](1024)(42)
    val juza = new java.util.zip.Adler32
    juza.update(data)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, 0, data.length)
    assertEquals(adler.getValue, expected)
  }
}
```

### Test Naming

Use descriptive names in backtick strings for `test(...)`:

```scala
test("deflate and inflate with Z_DEFAULT_COMPRESSION") { ... }
test("combine two Adler-32 checksums") { ... }
test("GZIPInputStream reads concatenated gzip streams") { ... }
```

### Run Specific Tests

```bash
# Single test suite, Scala 2.13
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite

# Single test suite, Scala 3
./mill core[3.3.7].test.testOnly com.jcraft.jzlib.Adler32Suite

# Run a specific test by name
./mill core[2.13.16].test.testOnly -- com.jcraft.jzlib.Adler32Suite '*combine*'
```

### Coverage Expectations

- Every public API method should have at least one test.
- Algorithm changes (in `Deflate`, `Inflate`, etc.) should include a round-trip test.
- Bug fixes must include a regression test.

## Pull Request Review

Before your PR can be merged it must:

1. ✅ Compile on Scala 2.13 and Scala 3 (`./mill __.compile`)
2. ✅ All tests pass (`./mill __.test`)
3. ✅ Scalafmt clean (`./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources`)
4. ✅ Include tests for new functionality or bug fixes
5. ✅ Follow the commit message format above
6. ✅ Reference the upstream jzlib commit if applicable

## Questions

Open a GitHub issue or start a discussion. We are happy to help.
