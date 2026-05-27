import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

fun parsePomProperties(xml: String): Map<String, String> {
    val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val result = mutableMapOf<String, String>()
    val nodes =
        (db.parse(InputSource(xml.reader()))
            .documentElement
            .getElementsByTagName("properties")
            .item(0) as? Element)
            ?.childNodes
            ?: return result
    for (i in 0 until nodes.length) {
        val n = nodes.item(i)
        if (n is Element) result[n.tagName] = n.textContent.trim()
    }
    return result
}

val pomVersions: Map<String, String> =
    parsePomProperties(
        providers.fileContents(layout.projectDirectory.file("../parent/pom.xml")).asText.get()
    )

fun pomVersion(key: String) = pomVersions[key] ?: error("Missing POM property: $key")

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.21.0")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.5")
    implementation("uk.co.real-logic:sbe-tool:${pomVersion("version.sbe")}")
}
