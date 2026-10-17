import buildlogic.artifactKind
import buildlogic.ArtifactKind
import buildlogic.TestJarPublishingExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins { `maven-publish` }

val publishedTestJar = extensions.create<TestJarPublishingExtension>("publishedTestJar")
val sourceSets = the<SourceSetContainer>()

val testsJar = tasks.register<Jar>("testsJar") { archiveClassifier = "tests" }

val tests =
  configurations.create("tests") {
    isCanBeConsumed = true
    isCanBeResolved = false
    artifactKind(project, ArtifactKind.TESTS)
  }

artifacts { add("tests", testsJar) }

extensions.configure<PublishingExtension> {
  // configureEach also covers the "maven" publication when java-conventions creates it later.
  publications.withType(MavenPublication::class.java).configureEach {
    if (name == "maven") artifact(testsJar)
  }
}

testsJar.configure {
  from(publishedTestJar.sourceSetName.flatMap { sourceSets.named(it) }.map { it.output })
}
