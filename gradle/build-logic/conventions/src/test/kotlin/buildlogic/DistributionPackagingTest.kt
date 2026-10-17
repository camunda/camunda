package buildlogic

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DistributionPackagingTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun shouldPreserveDistributionLayoutsPermissionsAndIncrementalOutputs() {
    // given
    assumeTrue(Files.getFileStore(tempDir).supportsFileAttributeView("posix"))
    val fixture = copyFixture()
    createInputs(fixture)
    val tasks =
      listOf(
        "stageCamunda",
        "stageOptimize",
        "camundaTar",
        "camundaZip",
        "optimizeTar",
        "optimizeZip",
      )

    // when
    val firstRun = run(fixture, "--configuration-cache", *tasks.toTypedArray())

    // then
    tasks.forEach { assertEquals(TaskOutcome.SUCCESS, firstRun.task(":$it")?.outcome) }
    assertCamundaDistribution(fixture)
    assertOptimizeDistribution(fixture)

    // when
    val unchangedRun = run(fixture, "--configuration-cache", *tasks.toTypedArray())

    // then
    tasks.forEach { assertEquals(TaskOutcome.UP_TO_DATE, unchangedRun.task(":$it")?.outcome) }
    assertTrue(unchangedRun.output.contains("Reusing configuration cache"))

    // when
    val sourceFile = fixture.resolve("input/camunda/config/defaults.yaml")
    Files.delete(sourceFile)
    val syncRun = run(fixture, "--configuration-cache", "stageCamunda")

    // then
    assertEquals(TaskOutcome.SUCCESS, syncRun.task(":stageCamunda")?.outcome)
    assertFalse(Files.exists(fixture.resolve("build/staged/camunda-zeebe/config/defaults.yaml")))
  }

  private fun assertCamundaDistribution(fixture: Path) {
    val stage = fixture.resolve("build/staged/camunda-zeebe")
    val expectedFiles =
      setOf("README.txt", "LICENSE.txt", "bin/camunda", "bin/camunda.bat", "config/defaults.yaml")
    assertEquals(expectedFiles, filesUnder(stage))
    assertEquals(493, mode(stage.resolve("bin/camunda")))
    assertEquals(420, mode(stage.resolve("bin/camunda.bat")))
    assertEquals(420, mode(stage.resolve("config/defaults.yaml")))

    val root = "camunda-zeebe-8.11.0-SNAPSHOT"
    val zip = fixture.resolve("build/distributions/camunda-zeebe-8.11.0-SNAPSHOT.zip")
    val tar = fixture.resolve("build/distributions/camunda-zeebe-8.11.0-SNAPSHOT.tar.gz")
    val expectedArchiveFiles = expectedFiles.map { "$root/$it" }.toSet()
    assertEquals(expectedArchiveFiles, zipFiles(zip))
    assertArchiveModes(zip, mapOf("$root/bin/camunda" to 493, "$root/bin/camunda.bat" to 420))
    assertTarFiles(tar, expectedArchiveFiles)
    assertTarModes(tar, mapOf("$root/bin/camunda" to 493, "$root/bin/camunda.bat" to 420))
  }

  private fun assertOptimizeDistribution(fixture: Path) {
    val stage = fixture.resolve("build/staged/camunda-optimize")
    val expectedFiles =
      setOf(
        "README.txt",
        "License.txt",
        "LicenseBook_Optimize.txt",
        "optimize-startup.sh",
        "optimize-startup.bat",
        "upgrade/upgrade.sh",
        "upgrade/upgrade.bat",
        "config/environment-config.yaml",
      )
    assertEquals(expectedFiles, filesUnder(stage))
    listOf(
        "optimize-startup.sh",
        "optimize-startup.bat",
        "upgrade/upgrade.sh",
        "upgrade/upgrade.bat",
      )
      .forEach { assertEquals(493, mode(stage.resolve(it))) }
    assertEquals(420, mode(stage.resolve("config/environment-config.yaml")))

    val zip = fixture.resolve("build/distributions/camunda-optimize-8.11.0-SNAPSHOT-production.zip")
    val tar =
      fixture.resolve("build/distributions/camunda-optimize-8.11.0-SNAPSHOT-production.tar.gz")
    assertEquals(expectedFiles, zipFiles(zip))
    val executableModes =
      expectedFiles.filter { it.endsWith(".sh") || it.endsWith(".bat") }.associateWith { 493 }
    assertArchiveModes(zip, executableModes)
    assertTarFiles(tar, expectedFiles)
    assertTarModes(tar, executableModes)
  }

  private fun assertArchiveModes(archive: Path, expected: Map<String, Int>) {
    expected.forEach { (name, expectedMode) ->
      assertEquals(expectedMode, zipMode(archive, name), name)
    }
  }

  private fun assertTarFiles(archive: Path, expected: Set<String>) {
    assertEquals(expected, tarModes(archive).keys, "TAR file entries")
  }

  private fun assertTarModes(archive: Path, expected: Map<String, Int>) {
    val entries = tarModes(archive)
    expected.forEach { (name, expectedMode) -> assertEquals(expectedMode, entries[name], name) }
  }

  private fun createInputs(fixture: Path) {
    writeInput(fixture, "input/camunda/README.txt")
    writeInput(fixture, "input/camunda/LICENSE.txt")
    writeInput(fixture, "input/camunda/config/defaults.yaml")
    writeInput(fixture, "input/camunda/bin/camunda")
    writeInput(fixture, "input/camunda/bin/camunda.bat")

    writeInput(fixture, "input/optimize/README.txt")
    writeInput(fixture, "input/optimize/License.txt")
    writeInput(fixture, "input/optimize/LicenseBook_Optimize.txt")
    writeInput(fixture, "input/optimize/config/environment-config.yaml")
    writeInput(fixture, "input/optimize/optimize-startup.sh")
    writeInput(fixture, "input/optimize/optimize-startup.bat")
    writeInput(fixture, "input/optimize/upgrade/upgrade.sh")
    writeInput(fixture, "input/optimize/upgrade/upgrade.bat")
  }

  private fun writeInput(fixture: Path, relativePath: String, mode: Int = 420) {
    val path = fixture.resolve(relativePath)
    path.parent.createDirectories()
    path.writeText(relativePath)
    val permissions = buildSet {
      if (mode and 0b100000000 != 0) add(PosixFilePermission.OWNER_READ)
      if (mode and 0b010000000 != 0) add(PosixFilePermission.OWNER_WRITE)
      if (mode and 0b001000000 != 0) add(PosixFilePermission.OWNER_EXECUTE)
      if (mode and 0b000100000 != 0) add(PosixFilePermission.GROUP_READ)
      if (mode and 0b000010000 != 0) add(PosixFilePermission.GROUP_WRITE)
      if (mode and 0b000001000 != 0) add(PosixFilePermission.GROUP_EXECUTE)
      if (mode and 0b000000100 != 0) add(PosixFilePermission.OTHERS_READ)
      if (mode and 0b000000010 != 0) add(PosixFilePermission.OTHERS_WRITE)
      if (mode and 0b000000001 != 0) add(PosixFilePermission.OTHERS_EXECUTE)
    }
    Files.setPosixFilePermissions(path, permissions)
  }

  private fun mode(path: Path): Int =
    Files.getPosixFilePermissions(path).fold(0) { mode, permission ->
      mode or
        when (permission) {
          PosixFilePermission.OWNER_READ -> 0b100000000
          PosixFilePermission.OWNER_WRITE -> 0b010000000
          PosixFilePermission.OWNER_EXECUTE -> 0b001000000
          PosixFilePermission.GROUP_READ -> 0b000100000
          PosixFilePermission.GROUP_WRITE -> 0b000010000
          PosixFilePermission.GROUP_EXECUTE -> 0b000001000
          PosixFilePermission.OTHERS_READ -> 0b000000100
          PosixFilePermission.OTHERS_WRITE -> 0b000000010
          PosixFilePermission.OTHERS_EXECUTE -> 0b000000001
        }
    }

  private fun filesUnder(directory: Path): Set<String> =
    Files.walk(directory).use { paths ->
      paths
        .filter { Files.isRegularFile(it) }
        .map { directory.relativize(it).toString().replace('\\', '/') }
        .toList()
        .toSet()
    }

  private fun zipFiles(archive: Path): Set<String> =
    ZipFile(archive.toFile()).use { zip ->
      val entries = zip.entries()
      buildList {
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.isDirectory) add(entry.name)
          }
        }
        .also { assertEquals(it.size, it.toSet().size, "duplicate ZIP entries") }
        .toSet()
    }

  private fun zipMode(archive: Path, target: String): Int {
    val bytes = Files.readAllBytes(archive)
    val endRecord =
      (bytes.size - 22 downTo maxOf(0, bytes.size - 65_557)).firstOrNull {
        littleEndian(bytes, it, 4) == 0x06054b50L
      } ?: throw IOException("ZIP end record is missing: $archive")
    var offset = littleEndian(bytes, endRecord + 16, 4).toInt()
    val entryCount = littleEndian(bytes, endRecord + 10, 2).toInt()
    repeat(entryCount) {
      if (littleEndian(bytes, offset, 4) != 0x02014b50L) {
        throw IOException("Invalid ZIP central directory: $archive")
      }
      val nameLength = littleEndian(bytes, offset + 28, 2).toInt()
      val extraLength = littleEndian(bytes, offset + 30, 2).toInt()
      val commentLength = littleEndian(bytes, offset + 32, 2).toInt()
      val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
      if (name == target) return ((littleEndian(bytes, offset + 38, 4) ushr 16) and 0xFFF).toInt()
      offset += 46 + nameLength + extraLength + commentLength
    }
    throw IOException("ZIP entry is missing: $target in $archive")
  }

  private fun tarModes(archive: Path): Map<String, Int> {
    val bytes = GZIPInputStream(Files.newInputStream(archive)).use { it.readBytes() }
    val result = mutableMapOf<String, Int>()
    val seenPaths = mutableSetOf<String>()
    var offset = 0
    while (offset + 512 <= bytes.size && bytes[offset] != 0.toByte()) {
      val name = tarString(bytes, offset, 100)
      val prefix = tarString(bytes, offset + 345, 155)
      val path = if (prefix.isEmpty()) name else "$prefix/$name"
      val mode = tarOctal(bytes, offset + 100, 8) and 0xFFF
      val size = tarOctal(bytes, offset + 124, 12)
      if (!seenPaths.add(path)) throw IOException("Duplicate TAR entry: $path in $archive")
      val typeFlag = bytes[offset + 156]
      if (typeFlag == 0.toByte() || typeFlag == '0'.code.toByte()) result[path] = mode
      offset += 512 + ((size + 511) / 512) * 512
    }
    return result
  }

  private fun tarString(bytes: ByteArray, offset: Int, length: Int): String =
    String(bytes, offset, length, Charsets.UTF_8).substringBefore('\u0000').trim()

  private fun tarOctal(bytes: ByteArray, offset: Int, length: Int): Int =
    tarString(bytes, offset, length).ifEmpty { "0" }.toInt(8)

  private fun littleEndian(bytes: ByteArray, offset: Int, length: Int): Long =
    (0 until length).fold(0L) { value, index ->
      value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * index))
    }

  private fun copyFixture(): Path {
    val sourceUrl =
      requireNotNull(javaClass.classLoader.getResource("fixtures/distribution-packaging")) {
        "Distribution packaging fixture is missing"
      }
    val source = Path.of(sourceUrl.toURI())
    val destination = tempDir.resolve("fixture")
    source.toFile().walkTopDown().forEach { file ->
      val relative = source.toFile().toPath().relativize(file.toPath())
      val target = destination.resolve(relative.toString())
      if (file.isDirectory) {
        target.createDirectories()
      } else {
        target.parent.createDirectories()
        file.copyTo(target.toFile())
      }
    }
    return destination
  }

  private fun run(fixture: Path, vararg arguments: String) =
    GradleRunner.create()
      .withProjectDir(fixture.toFile())
      .withArguments(*arguments, "--console=plain")
      .withPluginClasspath()
      .build()
}
