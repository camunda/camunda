plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
  id("buildlogic.parallel-test-classes-conventions")
}

dependencies {
  api(libs.org.junit.jupiter.junit.jupiter.api)
  api(libs.org.junit.jupiter.junit.jupiter.params)
  testImplementation(project(":zeebe-backup"))
  testImplementation(project(":camunda-cluster"))
  testImplementation(libs.org.apache.commons.commons.lang3)
  testImplementation(project(":zeebe-protocol"))
  testImplementation(project(":zeebe-util"))
}

description = "Zeebe Backup Store Testkit"
