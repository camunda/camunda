import buildlogic.DOCKER_IMAGE_USAGE
import buildlogic.dockerImageName
import buildlogic.useDockerImage
import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.testing.Test

// For modules whose integration tests start the Camunda container image: builds the image from the
// distribution before the tests run. The image id file is consumed from the distribution project
// through a variant, so no task path is referenced across projects.
val dockerImageProviders = configurations.dependencyScope("dockerImageProviders")

val dockerImageIdFiles =
  configurations.resolvable("dockerImageIdFiles") {
    extendsFrom(dockerImageProviders.get())
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(DOCKER_IMAGE_USAGE)) }
  }

dependencies { add(dockerImageProviders.name, project(":camunda-zeebe")) }

// A run that explicitly excludes the container tests must not need docker or podman.
val excludesContainerTests =
  providers
    .gradleProperty("excludedGroups")
    .map { groups -> groups.split(',').map(String::trim).contains("container") }
    .getOrElse(false)

tasks.withType<Test>().configureEach {
  if (name == "it" && !excludesContainerTests) {
    useDockerImage(dockerImageName(), dockerImageIdFiles.get())
  }
}
