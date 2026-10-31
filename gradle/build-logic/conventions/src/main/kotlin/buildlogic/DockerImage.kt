package buildlogic

import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** Usage attribute of the variant that publishes the id file of the container image. */
const val DOCKER_IMAGE_USAGE = "camunda-docker-image"

/** Image built by `buildDockerImage*` and used by the tests, overridable with -Pdocker.image. */
fun Project.dockerImageName(): Provider<String> =
  providers.gradleProperty("docker.image").orElse("camunda/camunda:current-test")

/**
 * Registers `buildDockerImage`, which builds the Camunda container image from the distribution tar
 * [distball]. The image is tagged with [dockerImageName].
 */
fun Project.registerBuildDockerImage(
  distball: Provider<RegularFile>,
): TaskProvider<BuildDockerImage> {
  val buildDockerImage =
    registerImageTask(
      "buildDockerImage",
      "Builds the Camunda container image without frontend assets, for tests " +
        "(-Pdocker.image=name:tag, -Pcontainer.cli=docker|podman).",
      distball,
    )

  // Container tests in other projects consume the id file, which makes them wait for the image.
  configurations.consumable("dockerImageElements") {
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, DOCKER_IMAGE_USAGE))
    }
    outgoing.artifact(buildDockerImage.flatMap { it.imageIdFile }) { builtBy(buildDockerImage) }
  }

  return buildDockerImage
}

/**
 * Registers `buildDockerImageRelease`, which builds the same image as `buildDockerImage` from a
 * distribution that includes the frontend assets. Requesting it turns the frontend build on, see
 * `buildlogic.frontend-webjar-conventions`; it fails if the frontend build is skipped anyway, e.g.
 * by -Pskip.fe.build, -Pquickly or an abbreviated task name that is not detected as the release.
 */
fun Project.registerBuildDockerImageRelease(
  distball: Provider<RegularFile>,
): TaskProvider<BuildDockerImage> {
  val skipFrontend = frontendBuildSkipped()
  return registerImageTask(
    "buildDockerImageRelease",
    "Builds the Camunda container image with frontend assets, for releases " +
      "(-Pdocker.image=name:tag, -Pcontainer.cli=docker|podman).",
    distball,
  ) {
    doFirst {
      check(!skipFrontend.get()) {
        "buildDockerImageRelease needs the frontend, but the frontend build is skipped; " +
          "spell the task name in full and remove -Pskip.fe.build and -Pquickly"
      }
    }
  }
}

private fun Project.registerImageTask(
  name: String,
  description: String,
  distball: Provider<RegularFile>,
  configure: BuildDockerImage.() -> Unit = {},
): TaskProvider<BuildDockerImage> =
  tasks.register(name, BuildDockerImage::class.java) {
    group = "distribution"
    this.description = description
    this.distball.set(distball)
    buildFiles.from(
      layout.settingsDirectory.file("camunda.Dockerfile"),
      layout.settingsDirectory.file("zeebe/docker/utils/jvm.options"),
    )
    image.set(dockerImageName())
    contextDirectory.set(layout.settingsDirectory)
    imageIdFile.set(layout.buildDirectory.file("docker/$name.iid"))
    configure()
  }
