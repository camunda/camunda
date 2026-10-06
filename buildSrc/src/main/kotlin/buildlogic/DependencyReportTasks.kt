package buildlogic

import groovy.json.JsonOutput
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrintGradleDependencyReportTask : DefaultTask() {
  @get:Input abstract val reportScope: Property<String>

  @get:Input abstract val projectName: Property<String>

  @get:Input abstract val projectDirectory: Property<String>

  @get:Input abstract val thirdParty: MapProperty<String, String>

  @get:Input abstract val directThirdParty: ListProperty<String>

  @get:Input abstract val internal: ListProperty<String>

  @TaskAction
  fun printReport() {
    val scope = reportScope.get()
    require(scope in setOf("compile", "runtime", "test")) {
      "Unsupported dependency report scope: $scope"
    }
    println(
      JsonOutput.toJson(
        mapOf(
          "project" to projectName.get(),
          "directory" to projectDirectory.get(),
          "scope" to scope,
          "third_party" to thirdParty.get(),
          "direct_third_party" to directThirdParty.get(),
          "internal" to internal.get(),
        )
      )
    )
  }
}

abstract class WriteDistributionDependencyReportTask : DefaultTask() {
  @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection

  @get:Input abstract val artifactMetadata: ListProperty<String>

  @get:Input abstract val excludedFilePrefixes: ListProperty<String>

  @get:Input abstract val fileNameReplacements: MapProperty<String, String>

  @get:Input abstract val projectPath: Property<String>

  @get:Input abstract val localJarEnabled: Property<Boolean>

  @get:Input abstract val localJarFileName: Property<String>

  @get:OutputFile abstract val reportFile: RegularFileProperty

  @TaskAction
  fun writeReport() {
    val availableFileNames =
      runtimeClasspath.files.filter(File::isFile).mapTo(mutableSetOf()) { it.name }
    val excludedPrefixes = excludedFilePrefixes.get()
    val replacements = fileNameReplacements.get()
    val artifacts =
      artifactMetadata
        .get()
        .map(DistributionDependencyReportArtifact::decode)
        .filter { it.fileName in availableFileNames }
        .filter { artifact -> excludedPrefixes.none { artifact.fileName.startsWith(it) } }
        .map { artifact ->
          mapOf<String, Any?>(
            "file" to (replacements[artifact.fileName] ?: artifact.fileName),
            "coordinate" to artifact.coordinate,
            "direct" to artifact.direct,
            "internal" to artifact.internal,
          )
        }
        .sortedBy { it["file"].toString() }

    val packagedArtifacts =
      if (localJarEnabled.get()) {
        artifacts +
          mapOf<String, Any?>(
            "file" to localJarFileName.get(),
            "coordinate" to "project:${projectPath.get()}",
            "direct" to true,
            "internal" to true,
          )
      } else {
        artifacts
      }

    val output = reportFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(JsonOutput.toJson(mapOf("artifacts" to packagedArtifacts)))
  }
}

data class DistributionDependencyReportArtifact(
  val fileName: String,
  val coordinate: String,
  val direct: Boolean,
  val internal: Boolean,
) {
  fun encode(): String =
    listOf(fileName, coordinate, direct.toString(), internal.toString()).joinToString(SEPARATOR)

  companion object {
    private const val SEPARATOR = "\u0000"

    fun decode(encoded: String): DistributionDependencyReportArtifact {
      val parts = encoded.split(SEPARATOR, limit = 4)
      require(parts.size == 4) { "Invalid distribution dependency report artifact: $encoded" }
      return DistributionDependencyReportArtifact(
        fileName = parts[0],
        coordinate = parts[1],
        direct = parts[2].toBooleanStrict(),
        internal = parts[3].toBooleanStrict(),
      )
    }
  }
}
