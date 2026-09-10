package buildlogic

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

fun <T> parsePom(xml: String, reader: (Element) -> T): T {
  val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(xml.reader()))
  return reader(root.documentElement)
}

fun parsePomProperties(xml: String): Map<String, String> =
  parsePom(xml) { root ->
    val result = mutableMapOf<String, String>()
    val properties = root.getElementsByTagName("properties").item(0) as? Element
    val nodes = properties?.childNodes ?: return@parsePom result
    for (i in 0 until nodes.length) {
      val node = nodes.item(i)
      if (node is Element) result[node.tagName] = node.textContent.trim()
    }
    result
  }

fun pomVersion(versions: Map<String, String>, key: String) =
  versions[key] ?: error("Missing POM property: $key")

fun parsePomElement(xml: String, elementName: String): String =
  parsePom(xml) { root ->
    root.getElementsByTagName(elementName).item(0)?.textContent?.trim()
      ?: error("Missing POM element: $elementName")
  }
