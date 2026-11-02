plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-backup"))
  implementation(project(":camunda-cluster"))
  api(libs.com.fasterxml.jackson.core.jackson.databind)
  testImplementation(project(":zeebe-protocol"))
  testImplementation(libs.net.jqwik.jqwik)
}

description = "Zeebe Backup Store Common"
