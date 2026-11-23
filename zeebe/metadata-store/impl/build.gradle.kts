plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  api(project(":zeebe-metadata-store-api"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Metadata Store Implementation"

mutationTesting {
  defaultTargetClasses.add("io.camunda.zeebe.metadata.store.*")
  crossModuleSourceDirs.from(layout.projectDirectory.dir("../api/src/main/java"))
}

// The tests of this module cover the api classes better than the api tests do, so PIT mutates them
// here too (-Ppit.crossModule=false turns this off).
dependencies { pitMutableCode(project(":zeebe-metadata-store-api")) }
