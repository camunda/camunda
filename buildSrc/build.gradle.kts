import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

fun parsePomProperties(xml: String): Map<String, String> {
  val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
  val root = db.parse(InputSource(xml.reader())).documentElement
  val properties =
    (0 until root.childNodes.length)
      .asSequence()
      .map { root.childNodes.item(it) }
      .filterIsInstance<Element>()
      .firstOrNull { it.tagName == "properties" } ?: return emptyMap()
  val result = mutableMapOf<String, String>()
  for (i in 0 until properties.childNodes.length) {
    val property = properties.childNodes.item(i)
    if (property is Element) {
      result[property.tagName] = property.textContent.trim()
    }
  }
  return result
}

val pomVersions: Map<String, String> =
  parsePomProperties(
    providers.fileContents(layout.projectDirectory.file("../parent/pom.xml")).asText.get()
  )

private val pomPropertyReference = Regex("""\$\{([^}]+)}""")

fun pomVersion(key: String): String {
  fun resolve(property: String, resolving: Set<String> = emptySet()): String {
    if (property in resolving) {
      error("Cyclic POM property reference: ${(resolving + property).joinToString(" -> ")}")
    }

    val value = pomVersions[property] ?: error("Missing POM property: $property")
    return pomPropertyReference.replace(value) { match ->
      resolve(match.groupValues[1], resolving + property)
    }
  }

  return resolve(key)
}

plugins { `kotlin-dsl` }

repositories {
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.8.0")
  implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
  implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
  implementation(
    "org.openapitools:openapi-generator-gradle-plugin:${pomVersion("plugin.version.openapi-generator")}"
  )
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.2")
  implementation("org.gradle:test-retry-gradle-plugin:1.6.6")
  implementation("uk.co.real-logic:sbe-tool:${pomVersion("version.sbe")}")
}
