plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.integration-test-module-conventions")
}

dependencies {
  testImplementation(project(":zeebe-event-plane-segment"))
  testImplementation(project(":zeebe-event-plane-common"))
  testImplementation(project(":zeebe-event-plane-consumer"))
  testImplementation(project(":zeebe-event-plane-maintenance"))
  testImplementation(project(":zeebe-event-plane-service"))
  testImplementation(project(":zeebe-qa-util"))
  testImplementation(project(":camunda-client-java"))
  testImplementation(project(":configuration"))
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.org.testcontainers.testcontainers)
  testImplementation(libs.org.testcontainers.testcontainers.junit.jupiter)
  testImplementation(libs.org.testcontainers.testcontainers.localstack)
  testImplementation(project(":zeebe-restore-standalone"))
  testImplementation(project(":zeebe-journal"))
  testImplementation(libs.io.micrometer.micrometer.core)
  testImplementation(project(":zeebe-bpmn-model"))
  testImplementation(project(":zeebe-protocol"))
  testImplementation(project(":zeebe-backup"))
  testImplementation(project(":zeebe-broker"))
  testImplementation(libs.org.agrona.agrona)
  testImplementation(libs.org.jspecify.jspecify)
  testImplementation(project(":camunda-zeebe"))
}

description = "Zeebe Event Plane QA"
