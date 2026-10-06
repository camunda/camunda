import buildlogic.DistributionDependencyReportExtension
import buildlogic.WriteDistributionDependencyReportTask
import buildlogic.distributionReportArtifacts
import org.gradle.jvm.tasks.Jar

val distributionDependencyReport =
  extensions.create<DistributionDependencyReportExtension>("distributionDependencyReport")
val runtimeClasspathConfiguration = configurations.getByName("runtimeClasspath")
val distributionJar = tasks.named<Jar>("jar")
val runtimeResolutionRoot = runtimeClasspathConfiguration.incoming.resolutionResult.rootComponent
val runtimeArtifacts = runtimeClasspathConfiguration.incoming.artifacts.resolvedArtifacts

tasks.register<WriteDistributionDependencyReportTask>("writeDistDependencyReport") {
  group = "help"
  description = "Writes resolved runtime dependencies for the packaged distribution."
  dependsOn(distributionJar)
  dependsOn(runtimeClasspathConfiguration.buildDependencies)

  runtimeClasspath.from(runtimeClasspathConfiguration)
  artifactMetadata.set(
    runtimeResolutionRoot
      .zip(runtimeArtifacts) { root, artifacts ->
        root
          .distributionReportArtifacts(artifacts)
          .sortedWith(compareBy({ it.fileName }, { it.coordinate }, { it.direct }, { it.internal }))
          .map { it.encode() }
      }
  )
  excludedFilePrefixes.set(distributionDependencyReport.excludedFilePrefixes)
  fileNameReplacements.set(distributionDependencyReport.fileNameReplacements)
  projectPath.set(project.path)
  localJarEnabled.set(distributionJar.map { it.enabled })
  localJarFileName.set(distributionJar.flatMap { it.archiveFile }.map { it.asFile.name })
  reportFile.set(layout.buildDirectory.file("reports/dist-dependencies.json"))
}
