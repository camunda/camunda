package io.camunda.gradle.pom

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

private val propertyReferencePattern = Regex("""\$\{([^}]+)}""")

class PomResolver(private val pomXml: String) {
  private val documentElement: Element = parsePom(pomXml)
  private val rawProperties: Map<String, String> = extractProperties(documentElement)

  fun properties(): Map<String, String> = rawProperties.toMap()

  fun projectVersion(): String? {
    return documentElement.findDirectChild("version")?.textContent?.trim()?.takeIf { it.isNotBlank() }
      ?: documentElement.findDirectChild("parent")?.findDirectChild("version")?.textContent?.trim()
        ?.takeIf { it.isNotBlank() }
  }

  fun elementText(elementName: String): String? {
    val element = documentElement.getElementsByTagName(elementName).item(0) ?: return null
    return element.textContent.trim().takeIf { it.isNotBlank() }
  }

  fun resolveProperty(propertyName: String): String = resolvePomProperty(propertyName, rawProperties)

  fun resolveProperty(propertyName: String, vararg propertyMaps: Map<String, String>): String =
    resolvePomProperty(propertyName, *propertyMaps)

  fun resolveProperties(vararg propertyMaps: Map<String, String>): Map<String, String> {
    val resolvedProperties = mergePropertyMaps(propertyMaps.toList())
    return resolvedProperties.keys.associateWith { key ->
      resolvePomProperty(key, resolvedProperties)
    }
  }
}

fun resolvePomProperty(propertyName: String, vararg propertyMaps: Map<String, String>): String =
  resolvePomProperty(propertyName, mergePropertyMaps(propertyMaps.toList()), emptyList())

private fun resolvePomProperty(
  propertyName: String,
  properties: Map<String, String>,
  resolving: List<String>,
): String {
  if (propertyName in resolving) {
    error("Cyclic POM property reference: ${(resolving + propertyName).joinToString(" -> ")}")
  }

  val rawValue = properties[propertyName] ?: error("Missing POM property: $propertyName")
  return propertyReferencePattern.replace(rawValue) { match ->
    resolvePomProperty(match.groupValues[1], properties, resolving + propertyName)
  }
}

private fun parsePom(xml: String): Element {
  val dbFactory = DocumentBuilderFactory.newInstance()
  return dbFactory.newDocumentBuilder().parse(InputSource(xml.reader())).documentElement
}

private fun extractProperties(root: Element): Map<String, String> {
  val propertiesElement = root.findDirectChild("properties") ?: return emptyMap()
  val result = linkedMapOf<String, String>()
  val nodes = propertiesElement.childNodes
  for (i in 0 until nodes.length) {
    val node = nodes.item(i)
    if (node is Element) {
      result[node.tagName] = node.textContent.trim()
    }
  }
  return result
}

private fun mergePropertyMaps(propertyMaps: List<Map<String, String>>): Map<String, String> {
  val merged = linkedMapOf<String, String>()
  propertyMaps.forEach { propertyMap -> merged.putAll(propertyMap) }
  return merged
}

private fun Element.findDirectChild(name: String): Element? {
  val children = childNodes
  for (i in 0 until children.length) {
    val child = children.item(i)
    if (child is Element && child.tagName == name) {
      return child
    }
  }
  return null
}
