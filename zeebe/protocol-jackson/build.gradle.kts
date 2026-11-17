import buildlogic.projectArtifact
import buildlogic.ArtifactKind
plugins { id("buildlogic.server-conventions") }

java { disableAutoTargetJvm() }
tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
  implementation(project(":zeebe-protocol"))
  api(libs.com.fasterxml.jackson.core.jackson.annotations)
  api(libs.com.fasterxml.jackson.core.jackson.databind)
  api(libs.com.fasterxml.jackson.core.jackson.core)
  testImplementation(project(":zeebe-protocol-test-util"))
  testImplementation(projectArtifact(":zeebe-protocol-asserts", ArtifactKind.GENERATED_ASSERTIONS))
}

description = "Zeebe Protocol Jackson"
