plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  api(project(":zeebe-metadata-store-api"))
  implementation(libs.org.jspecify.jspecify)

  testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
}

description = "Zeebe Cluster Metadata Store Implementation"

mutationTesting { defaultTargetClasses.add("io.camunda.zeebe.metadata.store.impl.*") }
