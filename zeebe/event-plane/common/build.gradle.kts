plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-event-plane-segment"))
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.software.amazon.awssdk.s3)
  implementation(libs.software.amazon.awssdk.sdk.core)
  implementation(libs.software.amazon.awssdk.aws.core)
  implementation(libs.software.amazon.awssdk.regions)
  implementation(libs.software.amazon.awssdk.auth)
  implementation(libs.software.amazon.awssdk.http.client.spi)
  implementation(libs.software.amazon.awssdk.apache.client)
  testImplementation(libs.org.testcontainers.testcontainers)
  testImplementation(libs.org.testcontainers.testcontainers.junit.jupiter)
  testImplementation(libs.org.testcontainers.testcontainers.localstack)
}

description = "Zeebe Event Plane Common"
