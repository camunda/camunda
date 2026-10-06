package buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DependencyReportConventionsTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun shouldInvalidateDistributionReportWhenMetadataOrExclusionsChange() {
    // given
    val fixture = copyFixture()

    // when
    val first = run(fixture, "-Pfixture.version=1.0", "writeDistDependencyReport")

    // then
    assertEquals(TaskOutcome.SUCCESS, first.task(":writeDistDependencyReport")?.outcome)
    val firstReport = report(fixture)
    assertTrue(firstReport.contains("dependency-1.0.jar"))
    assertTrue(firstReport.contains("fixture-1.0.jar"))

    // when
    val versionChanged = run(fixture, "-Pfixture.version=2.0", "writeDistDependencyReport")

    // then
    assertEquals(TaskOutcome.SUCCESS, versionChanged.task(":writeDistDependencyReport")?.outcome)
    val versionChangedReport = report(fixture)
    assertTrue(versionChangedReport.contains("dependency-2.0.jar"))
    assertTrue(versionChangedReport.contains("fixture-2.0.jar"))
    assertTrue(!versionChangedReport.contains("dependency-1.0.jar"))

    // when
    fixture.resolve("report-config.txt").writeText("dependency-2.0.jar\n")
    val exclusionsChanged = run(fixture, "-Pfixture.version=2.0", "writeDistDependencyReport")

    // then
    assertEquals(TaskOutcome.SUCCESS, exclusionsChanged.task(":writeDistDependencyReport")?.outcome)
    assertTrue(!report(fixture).contains("dependency-2.0.jar"))

    // when
    val unchanged = run(fixture, "-Pfixture.version=2.0", "writeDistDependencyReport")

    // then
    assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":writeDistDependencyReport")?.outcome)
  }

  @Test
  fun shouldReuseConfigurationCacheForDependencyReports() {
    // given
    val fixture = copyFixture()

    // when
    run(
      fixture,
      "--configuration-cache",
      "-Pfixture.version=1.0",
      "writeDistDependencyReport",
    )
    val reused =
      run(
        fixture,
        "--configuration-cache",
        "-Pfixture.version=1.0",
        "writeDistDependencyReport",
      )

    // then
    assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":writeDistDependencyReport")?.outcome)
    assertTrue(reused.output.contains("Reusing configuration cache"))
  }

  @Test
  fun shouldPrintTheModuleReportWithTheExistingSchema() {
    // given
    val fixture = copyFixture()

    // when
    val result =
      run(
        fixture,
        "-Pfixture.version=1.0",
        "-Pdependency.report.scope=runtime",
        "printGradleDependencyReportEntry",
      )

    // then
    assertEquals(TaskOutcome.SUCCESS, result.task(":printGradleDependencyReportEntry")?.outcome)
    assertTrue(result.output.contains("\"project\":\"reporting-fixture\""))
    assertTrue(result.output.contains("\"scope\":\"runtime\""))
  }

  private fun run(fixture: Path, vararg arguments: String) =
    GradleRunner.create()
      .withProjectDir(fixture.toFile())
      .withArguments(*arguments, "--console=plain")
      .withPluginClasspath()
      .build()

  private fun report(fixture: Path): String =
    fixture.resolve("build/reports/dist-dependencies.json").readText()

  private fun copyFixture(): Path {
    val sourceUrl =
      requireNotNull(javaClass.classLoader.getResource("fixtures/reporting")) {
        "Reporting fixture is missing"
      }
    val source = Path.of(sourceUrl.toURI())
    val destination = tempDir.resolve("fixture-${Files.list(tempDir).use { it.count() }}")
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
}
