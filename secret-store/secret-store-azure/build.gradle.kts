plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":camunda-secret-store-api"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.com.azure.azure.security.keyvault.secrets)
  implementation(libs.com.azure.azure.identity)

  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  testRuntimeOnly(libs.org.junit.jupiter.junit.jupiter.engine)
  testImplementation(libs.org.assertj.assertj.core)
}

description = "Camunda Secret Store Azure Key Vault Implementation"
