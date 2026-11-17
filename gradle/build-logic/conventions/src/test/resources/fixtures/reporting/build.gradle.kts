import buildlogic.DistributionDependencyReportExtension

plugins {
  id("java")
  id("buildlogic.dependency-report-conventions")
  id("buildlogic.distribution-dependency-report-conventions")
}

version = providers.gradleProperty("fixture.version").getOrElse("1.0")

subprojects {
  apply(plugin = "java")
  version = rootProject.version
}

dependencies { runtimeOnly(project(":dependency")) }

val excludedPrefixes =
  providers.fileContents(layout.projectDirectory.file("report-config.txt")).asText.map { text ->
    text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
  }

extensions.configure<DistributionDependencyReportExtension> {
  excludedFilePrefixes.set(excludedPrefixes)
}
