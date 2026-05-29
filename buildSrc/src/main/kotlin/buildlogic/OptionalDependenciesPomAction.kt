package buildlogic

import groovy.util.Node
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.XmlProvider

class OptionalDependenciesPomAction(
    private val optionalDependencies: Set<String>,
) : Action<XmlProvider>, Serializable {

    override fun execute(xmlProvider: XmlProvider) {
        xmlProvider.asNode().optionalize(optionalDependencies)
    }

    private fun Node.optionalize(optionalDependencies: Set<String>) {
        val dependenciesNode =
            children().filterIsInstance<Node>().firstOrNull { it.hasName("dependencies") }
                ?: return
        dependenciesNode.children().filterIsInstance<Node>().forEach { dependencyNode ->
            val groupId = dependencyNode.childText("groupId")
            val artifactId = dependencyNode.childText("artifactId")
            if ("$groupId:$artifactId" in optionalDependencies) {
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
