import buildlogic.projectArtifact
import buildlogic.ArtifactKind

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  api(project(":zeebe-workflow-engine-state-mutable-api"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-stream-platform"))
  implementation(project(":camunda-security-protocol"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(project(":zeebe-bpmn-model"))
  implementation(libs.org.apache.commons.commons.lang3)
  implementation(project(":zeebe-workflow-engine-model"))
  implementation(project(":zeebe-workflow-engine-state-api"))
  testImplementation(project(":zeebe-workflow-engine-state"))
  testImplementation(projectArtifact(":zeebe-workflow-engine-state", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-protocol-asserts", ArtifactKind.GENERATED_ASSERTIONS))
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
  testImplementation(projectArtifact(":zeebe-workflow-engine-model", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-workflow-engine-state-api", ArtifactKind.TESTS))
}

description = "Zeebe Workflow Engine Appliers: event appliers that fold records into state"
