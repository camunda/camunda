plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":camunda-secret-store-api"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.com.azure.azure.security.keyvault.secrets)
  implementation(libs.com.azure.azure.identity)
  implementation(libs.com.azure.azure.core)
  implementation(libs.io.projectreactor.reactor.core)

  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  testRuntimeOnly(libs.org.junit.jupiter.junit.jupiter.engine)
  testImplementation(libs.org.assertj.assertj.core)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.api)
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Camunda Secret Store Azure Key Vault Implementation"
