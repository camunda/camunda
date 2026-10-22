package buildlogic

import java.io.File
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider

/** Usage attribute of the variant that publishes the id file of the container image. */
const val DOCKER_IMAGE_USAGE = "camunda-docker-image"

/** Image built by `buildDockerImage` and used by the tests, overridable with -Pdocker.image. */
fun Project.dockerImageName(): Provider<String> =
  providers.gradleProperty("docker.image").orElse("camunda/camunda:current-test")

/**
 * Registers `buildDockerImage`, which builds the Camunda container image from `camunda.Dockerfile`
 * and the distribution tar [distball]. The image is tagged with [dockerImageName] and built with
 * docker if installed, else podman (-Pcontainer.cli).
 */
fun Project.registerBuildDockerImage(distball: Provider<RegularFile>): TaskProvider<Exec> {
  val image = dockerImageName()
  val imageIdFile = layout.buildDirectory.file("docker/image.iid")
  val repoRoot = rootDir
  // docker if installed, else podman; `build` is common to both, unlike `buildx`
  val cli =
    providers
      .gradleProperty("container.cli")
      .orElse(
        providers.environmentVariable("PATH").map { path ->
          listOf("docker", "podman").firstOrNull { name ->
            path.split(File.pathSeparator).any { File(it, name).canExecute() }
          } ?: "docker"
        }
      )

  val buildDockerImage = tasks.register("buildDockerImage", Exec::class.java) {
    group = "distribution"
    description =
      "Builds the Camunda container image (-Pdocker.image=name:tag, -Pcontainer.cli=docker|podman)."
    // The image lives in the container daemon, so the task is only skipped when up to date, never
    // restored from the build cache. The image id file is the output that records the last build.
    inputs.file(distball).withPropertyName("distball").withPathSensitivity(PathSensitivity.NONE)
    inputs
      .files(
        layout.settingsDirectory.file("camunda.Dockerfile"),
        layout.settingsDirectory.file("zeebe/docker/utils/jvm.options"),
      )
      .withPropertyName("dockerBuildFiles")
      .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("image", image)
    inputs.property("cli", cli)
    outputs.file(imageIdFile).withPropertyName("imageIdFile")
    workingDir = repoRoot
    doFirst { executable = cli.get() }
    args("build", "--target", "app", "-f", "camunda.Dockerfile")
    argumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
          "-t",
          image.get(),
          "--iidfile",
          imageIdFile.get().asFile.absolutePath,
          "--build-arg",
          "DISTBALL=${repoRoot.toPath().relativize(distball.get().asFile.toPath())}",
          ".",
        )
      }
    )
  }

  // Container tests in other projects consume the id file, which makes them wait for the image.
  configurations.consumable("dockerImageElements") {
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, DOCKER_IMAGE_USAGE)) }
    outgoing.artifact(imageIdFile) { builtBy(buildDockerImage) }
  }

  return buildDockerImage
}
