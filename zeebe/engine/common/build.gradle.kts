plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-stream-platform"))
  implementation(libs.io.camunda.security.library.api)
  implementation(project(":camunda-security-protocol"))
  implementation(libs.io.camunda.security.library.validation)
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  testImplementation(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.openjdk.jmh.jmh.generator.annprocess)
}

description = "Zeebe Workflow Engine Common: contract types shared by the engine and its API"
