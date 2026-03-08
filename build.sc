import $ivy.`com.lihaoyi::mill-contrib-jmh:0.12.11`

import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.scalajslib.api._
import mill.scalanativelib._
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.publish._
import mill.contrib.jmh.JmhModule

val scalaVersions = Seq("2.13.16", "3.3.7")
val munitVersion  = "1.0.3"

/** Shared compiler options and formatting for all modules. */
trait ZlibModule extends ScalaModule with ScalafmtModule {
  def publishVersion = "1.1.5"
  def organization   = "com.github.scala-zlib"

  def scalacOptions = T {
    super.scalacOptions() ++ Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
    ) ++ (if (scalaVersion().startsWith("2."))
      Seq("-Xlint", "-Ywarn-dead-code", "-Ywarn-numeric-widen")
    else
      Seq("-Wunused:all", "-source:3.0-migration"))
  }
}

/** Cross-platform core algorithm module — runs on JVM, JS, Native, and WASM.
  * No java.io dependencies; pure byte-array algorithm only.
  */
trait CoreModule extends ZlibModule {
  def artifactName = "scala-zlib-core"

  trait CoreTests extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::${munitVersion}")
  }
}

/** JVM-only stream-wrapper module — extends java.io FilterStream classes. */
trait JvmStreamsModule extends ZlibModule {
  def artifactName = "scala-zlib-jvm"

  trait JvmTests extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::${munitVersion}")
  }
}

// ─── JVM ────────────────────────────────────────────────────────────────────

object core extends Cross[CoreJvmModule](scalaVersions)
trait CoreJvmModule extends CrossScalaModule with CoreModule {
  object test extends CoreTests
}

object jvm extends Cross[JvmStreamsJvmModule](scalaVersions)
trait JvmStreamsJvmModule extends CrossScalaModule with JvmStreamsModule {
  def moduleDeps = Seq(core(crossScalaVersion))
  object test extends JvmTests {
    def moduleDeps = super.moduleDeps ++ Seq(core(crossScalaVersion))
  }
}

// ─── JMH Benchmarks ─────────────────────────────────────────────────────────

object bench extends ScalaModule with JmhModule with ScalafmtModule {
  private val benchScalaVersion = scalaVersions.head
  def scalaVersion = benchScalaVersion
  def jmhCoreVersion = "1.37"
  def moduleDeps = Seq(core(benchScalaVersion))
}

// ─── Scala.js ───────────────────────────────────────────────────────────────

object coreJS extends Cross[CoreJsModule](scalaVersions)
trait CoreJsModule extends CrossScalaModule with CoreModule with ScalaJSModule {
  def scalaJSVersion = "1.20.0"
  object test extends ScalaJSTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::${munitVersion}")
  }
}

// ─── Scala.js WASM ──────────────────────────────────────────────────────────

object coreWASM extends Cross[CoreWasmModule](scalaVersions)
trait CoreWasmModule extends CrossScalaModule with CoreModule with ScalaJSModule {
  def scalaJSVersion = "1.20.0"
  override def moduleKind = ModuleKind.ESModule
  override def moduleSplitStyle = ModuleSplitStyle.FewestModules
  override def scalaJSExperimentalUseWebAssembly = true
  object test extends ScalaJSTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::${munitVersion}")
  }
}

// ─── Scala Native ───────────────────────────────────────────────────────────

object coreNative extends Cross[CoreNativeModule](scalaVersions)
trait CoreNativeModule extends CrossScalaModule with CoreModule with ScalaNativeModule {
  def scalaNativeVersion = "0.5.10"
  object test extends ScalaNativeTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::${munitVersion}")
  }
}
