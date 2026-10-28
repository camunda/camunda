import buildlogic.skipWhen
import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask
import io.camunda.gradle.flags.asEnabledFlag
import buildlogic.requiredVersion
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.tasks.Jar

plugins {
  id("buildlogic.server-conventions")
  id("com.github.node-gradle.node")
}

interface FrontendWebjarExtension {
  val frontendBuildDirectory: DirectoryProperty
  val frontendPackagedDirectory: DirectoryProperty
  val resourceTargetPath: Property<String>
}

val frontendWebjar = extensions.create<FrontendWebjarExtension>("frontendWebjar")
val frontendBuildDirectory = frontendWebjar.frontendBuildDirectory
val frontendPackagedDirectory = frontendWebjar.frontendPackagedDirectory
val resourceTargetPath = frontendWebjar.resourceTargetPath

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val parentNodeVersion = versionCatalog.requiredVersion("parent-node").removePrefix("v")
val parentNpmVersion = versionCatalog.requiredVersion("parent-npm")

val nodeExtension = extensions.getByType<NodeExtension>()

extensions.configure<NodeExtension> {
  download.set(true)
  version.set(parentNodeVersion)
  npmVersion.set(parentNpmVersion)
  distBaseUrl.set(null as String?)
  workDir.set(layout.settingsDirectory.dir(".gradle/nodejs/${project.name}"))
  npmWorkDir.set(layout.settingsDirectory.dir(".gradle/npm/${project.name}"))
  nodeProjectDir.set(layout.projectDirectory)
}

// Tests only exercise the backend, so the frontend is built only for a release image or when
// -Pskip.fe.build=false asks for it. The requested tasks are the only input known when the build is
// configured, and they are part of the configuration cache key.
val releaseImageRequested =
  gradle.startParameter.taskRequests.any { request ->
    request.args.any { it.substringAfterLast(':') == "buildDockerImageRelease" }
  }

val skipFrontendBuild =
  providers
    .gradleProperty("skip.fe.build")
    .orElse(providers.gradleProperty("quickly"))
    .asEnabledFlag()
    .orElse(!releaseImageRequested)

// The plugin's setup tasks are dependencies of the guarded tasks below and would still download
// Node and npm when those are skipped.
tasks.named("nodeSetup") { skipWhen(skipFrontendBuild) }

tasks.named("npmSetup") { skipWhen(skipFrontendBuild) }

val npmVersionPackage =
  tasks.register<NpmTask>("npmVersionPackage") {
    skipWhen(skipFrontendBuild)
    dependsOn(tasks.named("npmSetup"))
    args.set(
      listOf("version", project.version.toString(), "--no-git-tag-version", "--allow-same-version")
    )
    inputs.property("projectVersion", project.version)
    outputs.file(layout.projectDirectory.file("package.json"))
    // This task mutates a source-tree file, so caching it could restore a stale package.json.
    outputs.cacheIf { false }
  }

val npmCi =
  tasks.register<NpmTask>("npmCi") {
    skipWhen(skipFrontendBuild)
    dependsOn(npmVersionPackage)
    args.set(listOf("ci"))
    inputs.files(
      layout.projectDirectory.file("package.json"),
      layout.projectDirectory.file("package-lock.json"),
    )
    // Do not declare node_modules as an output: it contains symlinked workspace packages and
    // causes Gradle's file-system watcher to register paths more than once. Without outputs,
    // npm ci is intentionally run whenever this task is requested.
    outputs.cacheIf { false }
  }

val npmBuild =
  tasks.register<NpmTask>("npmBuild") {
    skipWhen(skipFrontendBuild)
    dependsOn(npmCi)
    args.set(listOf("run", "build"))
    inputs
      .files(
        provider {
          val excludes = mutableListOf("node_modules/**", "target/**", ".gradle/**", "build/**")
          if (frontendBuildDirectory.isPresent) {
            runCatching {
              val relPath =
                frontendBuildDirectory.get().asFile.relativeTo(layout.projectDirectory.asFile).path
              excludes.add("$relPath/**")
            }
          }
          fileTree(layout.projectDirectory) { exclude(excludes) }
        }
      )
      .withPropertyName("sourceFiles")
      .withPathSensitivity(PathSensitivity.RELATIVE)
    // The build output depends on the toolchain, so a Node/npm bump must not restore a cached build.
    inputs.property("nodeVersion", nodeExtension.version)
    inputs.property("npmVersion", nodeExtension.npmVersion)
    outputs.dir(frontendBuildDirectory)
    outputs.cacheIf { true }
  }

tasks.named<Jar>("jar") {
  mustRunAfter(npmBuild)
  from(frontendPackagedDirectory.orElse(frontendBuildDirectory)) { into(resourceTargetPath) }
}
