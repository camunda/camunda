import buildlogic.TestJarPublishingExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins {
    `maven-publish`
}

val publishedTestJar = extensions.create<TestJarPublishingExtension>("publishedTestJar")
val sourceSets = the<SourceSetContainer>()

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier = "tests"
}

val tests by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("tests", testsJar)
}

extensions.configure<PublishingExtension> {
    publications.withType(MavenPublication::class.java).named("maven") {
        artifact(testsJar)
    }
}

afterEvaluate {
    val sourceSet = sourceSets.named(publishedTestJar.sourceSetName.get()).get()
    tests.extendsFrom(configurations[sourceSet.runtimeClasspathConfigurationName])
    testsJar.configure {
        from(sourceSet.output)
    }
}
