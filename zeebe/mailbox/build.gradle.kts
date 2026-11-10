plugins {
  id("buildlogic.server-conventions")
}

dependencies {
  api(project(":zeebe-mailbox-api"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(projectArtifact(":zeebe-mailbox-api", ArtifactKind.TESTS))
}

description = "Zeebe Mailbox"
