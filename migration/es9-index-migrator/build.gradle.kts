plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":configuration"))
  implementation(project(":camunda-search-client-connect"))
  implementation(libs.co.elastic.clients.elasticsearch.java)
  implementation(libs.org.springframework.boot.spring.boot)
  implementation(libs.org.springframework.spring.core)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.info.picocli.picocli)
  implementation(libs.org.slf4j.slf4j.api)

  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.api)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.org.testcontainers.testcontainers)
  testImplementation(libs.org.testcontainers.testcontainers.elasticsearch)
  testImplementation(libs.org.assertj.assertj.core)
}

description = "ES9 Index Migrator"
