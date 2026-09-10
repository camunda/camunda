package buildlogic

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

fun <T> parsePom(xml: String, reader: (Element) -> T): T {
  val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(xml.reader()))
  return reader(root.documentElement)
}

private fun Element.directChild(name: String): Element? {
  val children = childNodes
  for (i in 0 until children.length) {
    val child = children.item(i)
    if (child is Element && child.tagName == name) {
      return child
    }
  }
  return null
}

fun parsePomProperties(xml: String): Map<String, String> =
  parsePom(xml) { root ->
    val result = mutableMapOf<String, String>()
    val properties = root.directChild("properties") ?: return@parsePom result
    val nodes = properties.childNodes
    for (i in 0 until nodes.length) {
      val node = nodes.item(i)
      if (node is Element) result[node.tagName] = node.textContent.trim()
    }
    result
  }

private val pomPropertyReference = Regex("""\$\{([^}]+)}""")

fun pomVersion(versions: Map<String, String>, key: String): String {
  fun resolve(property: String, resolving: Set<String> = emptySet()): String {
    if (property in resolving) {
      error("Cyclic POM property reference: ${(resolving + property).joinToString(" -> ")}")
    }

    val value = versions[property] ?: error("Missing POM property: $property")
    return pomPropertyReference.replace(value) { match ->
      resolve(match.groupValues[1], resolving + property)
    }
  }

  return resolve(key)
}

fun parsePomElement(xml: String, elementName: String): String =
  parsePom(xml) { root ->
    root.getElementsByTagName(elementName).item(0)?.textContent?.trim()
      ?: error("Missing POM element: $elementName")
  }
