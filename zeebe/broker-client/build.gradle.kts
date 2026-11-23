import buildlogic.projectArtifact
import buildlogic.ArtifactKind
plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  implementation(project(":camunda-cluster"))
  implementation(libs.org.jspecify.jspecify)
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(project(":zeebe-atomix-messaging-api"))
  implementation(project(":zeebe-atomix-membership-api"))
  implementation(project(":zeebe-atomix-utils"))
  api(project(":zeebe-cluster-config-api"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-scheduler"))
  api(project(":zeebe-transport"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-micrometer"))
  api(libs.io.micrometer.micrometer.commons)
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.opentelemetry.opentelemetry.api)
  implementation(libs.io.opentelemetry.opentelemetry.context)
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(project(":zeebe-test-util"))
  testImplementation(projectArtifact(":zeebe-cluster-config-api", ArtifactKind.TESTS))
  testImplementation(projectArtifact(":zeebe-scheduler", ArtifactKind.TESTS))
  testImplementation(project(":zeebe-protocol-test-util"))
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.io.opentelemetry.opentelemetry.sdk.testing)
  testImplementation("io.opentelemetry:opentelemetry-sdk-trace")
  testImplementation(libs.org.hamcrest.hamcrest)
}

description = "Zeebe Broker Client"
