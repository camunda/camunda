plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-restore"))
  implementation(project(":zeebe-broker"))
  implementation(project(":zeebe-cluster-config"))
  implementation(project(":zeebe-backup"))
  implementation(project(":camunda-cluster"))
  implementation(project(":zeebe-atomix-cluster"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-journal"))
  implementation(project(":zeebe-snapshots"))
  implementation(project(":zeebe-scheduler"))
  implementation(project(":zeebe-util"))
  implementation(project(":camunda-db-rdbms"))
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)

  testImplementation(project(":zeebe-restore", configuration = "tests"))
  testImplementation(project(":zeebe-protocol"))
  testImplementation(libs.org.assertj.assertj.core)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
}

description = "Zeebe Backup Restore Standalone"
