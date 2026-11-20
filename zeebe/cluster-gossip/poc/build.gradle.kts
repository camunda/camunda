plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-cluster-gossip-api"))
  api(project(":camunda-cluster"))
  api(project(":zeebe-atomix-membership-api"))
  api(project(":zeebe-atomix-messaging-api"))
  api(project(":zeebe-atomix-utils"))
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(project(":zeebe-cluster-gossip-testkit"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.io.micrometer.micrometer.core)
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe Cluster Gossip Proofs of Concept"
