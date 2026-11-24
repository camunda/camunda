plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  api(project(":zeebe-metadata-store-api"))
  api(project(":zeebe-scheduler"))
  implementation(project(":zeebe-metadata-store-impl"))
  implementation(project(":zeebe-util"))
  implementation(libs.org.jspecify.jspecify)

  testImplementation(project(":zeebe-metadata-store-testkit", configuration = "tests"))
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.net.jqwik.jqwik)
}

description = "Zeebe Cluster Metadata Store In-Memory"

mutationTesting { defaultTargetClasses.add("io.camunda.zeebe.metadata.store.memory.*") }
