import buildlogic.DistributionDependencyReportExtension
import buildlogic.filterMavenResources
import io.camunda.gradle.pom.PomResolver
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.optimize-conventions")
  id("buildlogic.distribution-dependency-report-conventions")
}

val parentPom = providers.fileContents(layout.settingsDirectory.file("parent/pom.xml"))
val optimizeParentPom = providers.fileContents(layout.settingsDirectory.file("optimize/pom.xml"))
val optimizePom = providers.fileContents(layout.projectDirectory.file("pom.xml"))
val parentProperties = PomResolver(parentPom.asText.get()).properties()
val optimizeParentProperties = PomResolver(optimizeParentPom.asText.get()).properties()
val optimizePomResolver = PomResolver(optimizePom.asText.get())
val optimizeVersion = project.version.toString()

extensions.configure<DistributionDependencyReportExtension> {
  fileNameReplacements.put(
    "upgrade-optimize-$optimizeVersion.jar",
    "upgrade-optimize-to-$optimizeVersion.jar",
  )
}

val optimizeElasticsearchVersion =
  optimizePomResolver.resolveProperty(
    "version.elasticsearch",
    parentProperties,
    optimizeParentProperties,
    optimizePomResolver.properties(),
  )
val optimizeDocsVersion =
  if (optimizeVersion.matches(Regex("\\d+\\.\\d+\\.\\d+-.+"))) {
    "develop"
  } else {
    optimizeVersion.substringBefore('-').replace(Regex("\\.\\d+$"), ".0")
  }
val resourceTokens =
  mapOf(
    "project.version" to optimizeVersion,
    "version.elasticsearch" to optimizeElasticsearchVersion,
    "docs.version" to optimizeDocsVersion,
  )

val optimizeDistroResources =
  tasks.register<Sync>("generateOptimizeDistroResources") {
    val task = this
    from("src") {
      this.filterMavenResources(task, "optimizeDistroResourceTokens", resourceTokens)
    }
    into(layout.buildDirectory.dir("generated/optimize-distro"))
  }

val optimizeRuntimeClasspath = configurations.named("runtimeClasspath")
val optimizeBackendJar =
  optimizeRuntimeClasspath.map { classpath ->
    classpath.incoming.artifactView {
      componentFilter { (it as? ProjectComponentIdentifier)?.projectPath == ":optimize-backend" }
    }.files
  }
val upgradeOptimizeJar =
  optimizeRuntimeClasspath.map { classpath ->
    classpath.incoming.artifactView {
      componentFilter { (it as? ProjectComponentIdentifier)?.projectPath == ":upgrade-optimize" }
    }.files
  }
val optimizeBackendResources =
  configurations.create("optimizeBackendResources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }

val optimizeScriptPaths =
  setOf(
    "optimize-startup.sh",
    "optimize-startup.bat",
    "upgrade/upgrade.sh",
    "upgrade/upgrade.bat",
  )

val optimizeScripts =
  copySpec {
    from(optimizeDistroResources) {
      include(optimizeScriptPaths)
      // Preserve Maven's 0755 mode for Optimize's .bat files as well as its .sh files.
      filePermissions { unix("0755".toInt(8)) }
    }
  }

val optimizeContents =
  copySpec {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(optimizeDistroResources) {
      exclude(optimizeScriptPaths)
    }
    with(optimizeScripts)
    from(optimizeBackendJar)
    from(upgradeOptimizeJar) {
      into("upgrade")
      rename { it.replaceFirst("upgrade-optimize-", "upgrade-optimize-to-") }
    }
    from({ configurations.runtimeClasspath.get() }) {
      into("lib")
      exclude("optimize-backend-*.jar", "upgrade-optimize-*.jar", "upgrade-optimize-to-*.jar")
    }
    from(optimizeBackendResources) {
      include("localization/**", "logo/**")
      into("config")
    }
  }

val assembleDist =
  tasks.register<Sync>("assembleDist") {
    group = "build"
    description = "Assemble the Camunda Optimize distribution"
    dependsOn(optimizeDistroResources)

    // Yarn builds only run when producing a dist artifact, not during tests or compilation.
    dependsOn(":optimize-client:yarnBuild")
    into(layout.buildDirectory.dir("camunda-optimize"))
    with(optimizeContents)
  }

val distTar =
  tasks.register<Tar>("distTar") {
    group = "build"
    description = "Create the Camunda Optimize tar.gz archive"
    dependsOn(assembleDist)
    compression = Compression.GZIP
    archiveBaseName.set("camunda-optimize")
    archiveVersion.set(optimizeVersion)
    archiveClassifier.set("production")
    archiveExtension.set("tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    with(optimizeContents)
  }

val distZip =
  tasks.register<Zip>("distZip") {
    group = "build"
    description = "Create the Camunda Optimize ZIP archive"
    dependsOn(assembleDist)
    archiveBaseName.set("camunda-optimize")
    archiveVersion.set(optimizeVersion)
    archiveClassifier.set("production")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    with(optimizeContents)
  }

dependencies {
  add(
    optimizeBackendResources.name,
    project(":optimize-backend", configuration = "distributionResources"),
  )
  implementation(project(":optimize-backend"))
  implementation(project(":upgrade-optimize"))
  implementation(libs.org.apache.logging.log4j.log4j.slf4j2.impl)
  implementation(libs.org.apache.logging.log4j.log4j.core)
}

group = "io.camunda.optimize"

description = "Optimize (Distro)"

tasks.named<Jar>("jar") { enabled = false }

tasks.named("assemble") { dependsOn(assembleDist, distTar, distZip) }
