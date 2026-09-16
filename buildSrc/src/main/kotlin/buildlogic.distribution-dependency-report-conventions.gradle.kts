import buildlogic.DistributionDependencyReportExtension
import groovy.json.JsonOutput
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.jvm.tasks.Jar

val distributionDependencyReport =
  extensions.create<DistributionDependencyReportExtension>("distributionDependencyReport")
val distributionDependencyReportFile =
  layout.buildDirectory.file("reports/dist-dependencies.json")
val distributionReportProject = project
val runtimeClasspathConfiguration =
  distributionReportProject.configurations.getByName("runtimeClasspath")
val distributionJar = distributionReportProject.tasks.named<Jar>("jar")

tasks.register("writeDistDependencyReport") {
  group = "help"
  description = "Writes resolved runtime dependencies for the packaged distribution."
  notCompatibleWithConfigurationCache(
    "The report resolves runtimeClasspath during task execution"
  )
  dependsOn(distributionJar)
  dependsOn(runtimeClasspathConfiguration.buildDependencies)
  outputs.file(distributionDependencyReportFile)

  doLast {
    val runtimeClasspath = runtimeClasspathConfiguration
    val resolution = runtimeClasspath.incoming.resolutionResult
    val directComponents =
      resolution.root.dependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .map { it.selected.id }
        .toSet()
    val excludedFilePrefixes = distributionDependencyReport.excludedFilePrefixes.get()
    val fileNameReplacements = distributionDependencyReport.fileNameReplacements.get()

    val artifacts =
      runtimeClasspath.incoming.artifacts.artifacts
        .filter { artifact ->
          artifact.file.isFile &&
            excludedFilePrefixes.none { artifact.file.name.startsWith(it) }
        }
        .map { artifact ->
          val component = artifact.id.componentIdentifier
          val coordinate =
            when (component) {
              is ModuleComponentIdentifier ->
                "${component.group}:${component.module}:${component.version}"
              is ProjectComponentIdentifier -> "project:${component.projectPath}"
              else -> component.displayName
            }
          mapOf<String, Any?>(
            "file" to (fileNameReplacements[artifact.file.name] ?: artifact.file.name),
            "coordinate" to coordinate,
            "direct" to (component in directComponents),
            "internal" to (component is ProjectComponentIdentifier),
          )
        }
        .sortedBy { it["file"].toString() }

    val jar = distributionJar.get()
    val packagedArtifacts =
      if (jar.enabled) {
        artifacts +
          mapOf<String, Any?>(
            "file" to jar.archiveFile.get().asFile.name,
            "coordinate" to "project:${distributionReportProject.path}",
            "direct" to true,
            "internal" to true,
          )
      } else {
        artifacts
      }
    val output = distributionDependencyReportFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(JsonOutput.toJson(mapOf("artifacts" to packagedArtifacts)))
  }
}
