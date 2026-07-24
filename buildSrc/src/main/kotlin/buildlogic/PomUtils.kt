package buildlogic

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

fun parsePomProperties(xml: String): Map<String, String> {
  val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
  val result = mutableMapOf<String, String>()
  val nodes =
    (db.parse(InputSource(xml.reader())).documentElement.getElementsByTagName("properties").item(0)
        as? Element)
      ?.childNodes ?: return result
  for (i in 0 until nodes.length) {
    val n = nodes.item(i)
    if (n is Element) result[n.tagName] = n.textContent.trim()
  }
  return result
}

fun pomVersion(versions: Map<String, String>, key: String) =
  versions[key] ?: error("Missing POM property: $key")
