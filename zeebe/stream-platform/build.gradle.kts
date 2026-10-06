import buildlogic.projectArtifact
import buildlogic.ArtifactKind
plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":camunda-cluster"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-scheduler"))
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-logstreams"))
  api(libs.com.fasterxml.jackson.core.jackson.annotations)
  implementation(project(":zeebe-msgpack-value"))
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.micrometer.micrometer.commons)
  testImplementation(project(":zeebe-bpmn-model"))
  testImplementation(project(":zeebe-msgpack-core"))
  testImplementation(libs.junit.junit)
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.org.junit.platform.junit.platform.commons)
  testImplementation(projectArtifact(":zeebe-logstreams", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-scheduler", ArtifactKind.TESTS))
}

description = "Zeebe Stream Platform"
