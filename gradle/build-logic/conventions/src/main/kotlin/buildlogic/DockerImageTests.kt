package buildlogic

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

/**
 * Points the container tests of this task at [image] and makes the task wait for it. The id file is
 * a task input, so the tests run again when the image changes.
 */
fun Test.useDockerImage(image: Provider<String>, imageIdFile: FileCollection) {
  inputs
    .files(imageIdFile)
    .withPropertyName("dockerImageId")
    .withPathSensitivity(PathSensitivity.NONE)
  val name = image.get()
  environment("CAMUNDA_TEST_DOCKER_IMAGE", name)
  environment("CAMUNDA_CONTAINER_IMAGE", name.substringBeforeLast(':'))
  environment("CAMUNDA_CONTAINER_VERSION", name.substringAfterLast(':'))
}
