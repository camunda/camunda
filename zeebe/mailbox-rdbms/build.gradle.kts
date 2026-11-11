import buildlogic.ArtifactKind
import buildlogic.projectArtifact

plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-mailbox-api"))
  implementation(libs.org.mybatis.mybatis)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(projectArtifact(":zeebe-mailbox-api", ArtifactKind.TESTS))
  testImplementation(libs.com.h2database.h2)
  testImplementation(libs.org.postgresql.postgresql)
  testImplementation(libs.org.liquibase.liquibase.core)
  testImplementation(libs.org.testcontainers.testcontainers)
  testImplementation(libs.org.testcontainers.testcontainers.postgresql)
  testImplementation(libs.org.apache.logging.log4j.log4j.core)
}

description = "Zeebe Mailbox RDBMS"
