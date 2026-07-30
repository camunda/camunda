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
  }

artifacts { add("tests", testsJar) }

extensions.configure<PublishingExtension> {
  publications.withType(MavenPublication::class.java).named("maven") { artifact(testsJar) }
}

afterEvaluate {
  val sourceSet = sourceSets.named(publishedTestJar.sourceSetName.get()).get()
  tests.extendsFrom(configurations[sourceSet.runtimeClasspathConfigurationName])
  testsJar.configure { from(sourceSet.output) }
}
