package buildlogic

import groovy.util.Node
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.XmlProvider

class OptionalDependenciesPomAction(
    private val optionalDependencies: Map<String, String>,
) : Action<XmlProvider>, Serializable {

    override fun execute(xmlProvider: XmlProvider) {
        xmlProvider.asNode().optionalize(optionalDependencies)
    }

    private fun Node.optionalize(optionalDependencies: Map<String, String>) {
        val dependenciesNode =
            children().filterIsInstance<Node>().firstOrNull { it.hasName("dependencies") }
                ?: error("Published POM has no dependencies element")
        val publishedDependencies =
            dependenciesNode.children().filterIsInstance<Node>().associateBy {
                "${it.childText("groupId")}:${it.childText("artifactId")}"
            }

        optionalDependencies.forEach { (coordinate, version) ->
            val dependencyNode =
                publishedDependencies[coordinate]
                    ?: dependenciesNode.appendNode("dependency").also {
                        val (groupId, artifactId) = coordinate.split(":", limit = 2)
                        it.appendNode("groupId", groupId)
                        it.appendNode("artifactId", artifactId)
                        it.appendNode("version", version)
                    }
            if (dependencyNode.childText("optional") != "true") {
                dependencyNode.appendNode("optional", "true")
            }
        }
    }

    private fun Node.childText(name: String): String? =
        children().filterIsInstance<Node>().firstOrNull { it.hasName(name) }?.text()

    private fun Node.hasName(name: String): Boolean = name().toString().endsWith(name)

    companion object {
        private const val serialVersionUID = 1L
    }
}
