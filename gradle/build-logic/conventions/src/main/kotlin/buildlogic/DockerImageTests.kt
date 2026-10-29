package buildlogic

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

/**
 * Points the container tests of this task at [image] and makes the task wait for it. The id file
 * and the image name are task inputs, so the tests run again when the image changes.
 */
fun Test.useDockerImage(image: Provider<String>, imageIdFile: FileCollection) {
  inputs
    .files(imageIdFile)
    .withPropertyName("dockerImageId")
    .withPathSensitivity(PathSensitivity.NONE)
  // Docker defaults an absent tag to latest; a colon before the last slash is a registry port.
  val name =
    image.map { raw -> if (raw.substringAfterLast('/').contains(':')) raw else "$raw:latest" }
  inputs.property("dockerImage", name)
  // environment() takes Any and would stringify a provider, so resolve it when the task runs.
  doFirst {
    val resolved = name.get()
    environment("CAMUNDA_TEST_DOCKER_IMAGE", resolved)
    environment("CAMUNDA_CONTAINER_IMAGE", resolved.substringBeforeLast(':'))
    environment("CAMUNDA_CONTAINER_VERSION", resolved.substringAfterLast(':'))
  }
}
