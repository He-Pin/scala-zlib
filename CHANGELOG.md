# Changelog

All notable changes to scala-zlib will be documented in this file.
This project is a Scala port of [jzlib](https://github.com/jruby/jzlib).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Cross-platform stream classes — all stream wrappers now available on JVM, Scala.js, Scala Native, and WASM
- `AutoCloseable` on `Deflater` and `Inflater` — enables `scala.util.Using` and try-with-resources
- Companion object factories: `Deflater()`, `Deflater(level)`, `Deflater(level, wrapperType)`, `Deflater.gzip()`, `Inflater()`, `Inflater(wrapperType)`, `Inflater.gzip()`, `Inflater.auto()`
- `GZIPInputStream` multi-member support — reads concatenated GZIP streams per RFC 1952 §2.2
- `@inline` on hot-path methods in `Deflate` (`smaller`, `put_byte`, `put_short`, `send_code`, `bi_flush`)
- `JZlib.deflateBound()` / `compressBound()` — upper bound on compressed size
- `JZlib.compress()` / `uncompress()` — one-shot compression utilities
- `JZlib.getErrorDescription()` — human-readable error messages for zlib return codes
- `Z_RLE` and `Z_FIXED` compression strategy constants
- `toString` on `Deflater` and `Inflater` for debugging (shows stream state summary)
- Runnable Scala examples (`example/` module): `BasicCompression`, `GZIPExample`, `ChecksumExample`, `UtilityExample`
- Cross-platform usage examples (`example/` module)
- Release workflow for Maven Central publishing with downloadable artifacts (JAR, source, doc)

### Fixed
- Concatenated/multi-member GZIP streams now handled correctly by `GZIPInputStream`
- Integer overflow in `compress` / `uncompress` when computing output buffer sizes
- CI Java 25 compatibility and fail-fast behavior

### Changed
- Upgraded Mill from 0.12.11 to 1.1.2
- Build file renamed from `build.sc` to `build.mill`
- CI: added Windows Scala Native testing
- CI: optimized caching strategy (platform and Scala version specific)
- CI: enhanced release workflow with artifact upload to GitHub Releases
- CI: Java 25 now tested natively (not just bytecode compat)
- CI: updated workflows for Mill 1.1.2

### Documentation
- Comprehensive Scaladoc on `ZStream` deprecated class
- Scaladoc added to all core public APIs (`Deflater`, `Inflater`, `JZlib`, `ZStream`, `GZIPHeader`, exceptions)
- Scaladoc added to JVM stream classes (`DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream`, `ZOutputStream`, `ZInputStream`)
- Deprecated API migration guide added to README
- Gradle dependency snippet and Performance section added to README
- Platform support documentation updated for cross-platform stream classes

### Testing
- `CompanionObjectSuite` — tests for `Deflater`/`Inflater` companion object factories
- `GZIPMultiMemberSuite` — tests for concatenated/multi-member GZIP stream reading
- Edge case tests for flush modes, checksums, and utilities
- `JRubyCompatSuite` — jruby API compatibility test suite (JVM)
- `JZlibUtilSuite` — tests for `deflateBound`, `compressBound`, `compress`, `uncompress`
- `StrategyConstantsSuite` — tests for `Z_RLE` and `Z_FIXED` strategy constants
- `ErrorDescriptionSuite` — tests for `getErrorDescription` return values
- `WrapperTypeSuite` — edge case tests for wrapper type handling
- JMH benchmarks expanded to cover one-shot utility functions

## [1.1.5] - 2026-03-08

### Added
- Pure Scala port of jzlib, supporting JVM, Scala.js, Scala Native, and WASM
- Cross-compilation for Scala 2.13.16 and 3.3.7
- Scala.js 1.20.0 support with WASM experimental backend
- Scala Native 0.5.10 support
- munit test suite with 71 tests across all platforms
- JMH benchmark suite for performance regression testing
- GitHub Actions CI for all platforms (JVM, JS, Native, WASM)
- Comprehensive documentation (README, CONTRIBUTING, CLAUDE.md)

### Changed
- Exception hierarchy: `GZIPException` and `ZStreamException` extend `Exception`
  instead of `java.io.IOException` for cross-platform compatibility
- `WrapperType` implemented as sealed abstract class (was Java enum)
- Java `switch` fall-through translated to while+match state machines
- `do-while` replaced with `while({ body; cond })()` for Scala 3 compatibility

### Upstream jzlib changes incorporated
- All 65 upstream jzlib commits through 1.1.5 are incorporated
- Thread-safe `gen_codes()` (upstream 26fa95d)
- `Tree.d_code` ArrayIndexOutOfBoundsException fix (upstream 8b205d6)
- Performance-optimized `Adler32.update()` with NMAX batching (upstream 6900f5d)
- `l_buf` as separate byte array for better cache locality (upstream e2fee8f)
- `GZIPInputStream.getModifiedTime()` with deprecated `getModifiedtime()` (upstream 1a5fb10)
- Format auto-detection via `W_ANY` / `DEF_WBITS+32` (upstream 99d6334, 389af32)
- Concatenated gzip stream revert matches upstream final state (upstream e39056b)

## Upstream History

For changes prior to the Scala port, see the original
[jzlib ChangeLog](https://github.com/jruby/jzlib/blob/master/ChangeLog).
