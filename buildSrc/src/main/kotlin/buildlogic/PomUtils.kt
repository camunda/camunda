package buildlogic

import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty

fun parsePomProperties(xml: String): Map<String, String> = PomResolver(xml).properties()

fun pomVersion(versions: Map<String, String>, key: String): String =
  resolvePomProperty(key, versions)

fun parsePomElement(xml: String, elementName: String): String =
  PomResolver(xml).elementText(elementName) ?: error("Missing POM element: $elementName")
