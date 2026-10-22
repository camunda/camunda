package buildlogic

import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** Usage attribute of the variant that publishes the id file of the container image. */
const val DOCKER_IMAGE_USAGE = "camunda-docker-image"

/** Image built by `buildDockerImage` and used by the tests, overridable with -Pdocker.image. */
fun Project.dockerImageName(): Provider<String> =
  providers.gradleProperty("docker.image").orElse("camunda/camunda:current-test")

/**
 * Registers `buildDockerImage`, which builds the Camunda container image from the distribution tar
 * [distball]. The image is tagged with [dockerImageName].
 */
fun Project.registerBuildDockerImage(
  distball: Provider<RegularFile>
): TaskProvider<BuildDockerImage> {
  val buildDockerImage =
    tasks.register("buildDockerImage", BuildDockerImage::class.java) {
      group = "distribution"
      description =
        "Builds the Camunda container image (-Pdocker.image=name:tag, -Pcontainer.cli=docker|podman)."
      this.distball.set(distball)
      buildFiles.from(
        layout.settingsDirectory.file("camunda.Dockerfile"),
        layout.settingsDirectory.file("zeebe/docker/utils/jvm.options"),
      )
      image.set(dockerImageName())
      contextDirectory.set(layout.settingsDirectory)
      imageIdFile.set(layout.buildDirectory.file("docker/image.iid"))
    }

  // Container tests in other projects consume the id file, which makes them wait for the image.
  configurations.consumable("dockerImageElements") {
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, DOCKER_IMAGE_USAGE))
    }
    outgoing.artifact(buildDockerImage.flatMap { it.imageIdFile }) { builtBy(buildDockerImage) }
  }

  return buildDockerImage
}
