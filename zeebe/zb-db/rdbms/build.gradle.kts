plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-micrometer"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.org.liquibase.liquibase.core)
  runtimeOnly(project(":camunda-db-rdbms-schema")) {
    exclude(group = "com.github.vertical-blank", module = "sql-formatter")
  }
  implementation(libs.org.mybatis.mybatis)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(libs.com.h2database.h2)
  testImplementation(libs.org.postgresql.postgresql)
  testImplementation(libs.org.testcontainers.testcontainers.postgresql)
}

description = "Zeebe DB RDBMS"
