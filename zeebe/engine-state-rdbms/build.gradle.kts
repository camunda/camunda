import buildlogic.ArtifactKind
import buildlogic.projectArtifact

plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-workflow-engine"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(libs.org.mybatis.mybatis)
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(projectArtifact(":zeebe-workflow-engine", ArtifactKind.TESTS))
  testImplementation(project(":zeebe-stream-platform"))
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.postgresql.postgresql)
  testImplementation(libs.org.liquibase.liquibase.core)
  testImplementation(libs.org.testcontainers.testcontainers)
  testImplementation(libs.org.testcontainers.testcontainers.postgresql)
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
}

description = "Zeebe Engine State RDBMS"
