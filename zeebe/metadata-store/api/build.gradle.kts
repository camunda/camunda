plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  api(project(":zeebe-scheduler"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Metadata Store API"

mutationTesting { defaultTargetClasses.add("io.camunda.zeebe.metadata.store.*") }
