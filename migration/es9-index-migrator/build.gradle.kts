plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.info.picocli.picocli)
  implementation(libs.org.slf4j.slf4j.api)

  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.assertj.assertj.core)
}

description = "ES9 Index Migrator"
