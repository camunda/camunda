package io.camunda.gradle.pom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PomResolverTest {
  @Test
  fun `should extract properties from a pom xml`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <groupId>io.camunda</groupId>
          <version>1.2.3</version>
        </properties>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)

    // then
    val properties = resolver.properties()
    assertEquals("io.camunda", properties["groupId"])
    assertEquals("1.2.3", properties["version"])
  }

  @Test
  fun `should resolve nested property references`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <baseVersion>1.2.3</baseVersion>
          <releaseVersion>${'$'}{baseVersion}-SNAPSHOT</releaseVersion>
          <finalVersion>${'$'}{releaseVersion}</finalVersion>
        </properties>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)

    // then
    assertEquals("1.2.3-SNAPSHOT", resolver.resolveProperty("releaseVersion"))
    assertEquals("1.2.3-SNAPSHOT", resolver.resolveProperty("finalVersion"))
  }

  @Test
  fun `should reject cyclic property references with a useful error message`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <first>${'$'}{second}</first>
          <second>${'$'}{first}</second>
        </properties>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)
    val exception =
      assertThrows(IllegalStateException::class.java) {
        resolver.resolveProperty("first")
      }

    // then
    assertEquals("Cyclic POM property reference: first -> second -> first", exception.message)
  }

  @Test
  fun `should report missing properties`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <present>value</present>
        </properties>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)
    val exception =
      assertThrows(IllegalStateException::class.java) {
        resolver.resolveProperty("missing")
      }

    // then
    assertEquals("Missing POM property: missing", exception.message)
  }

  @Test
  fun `should apply explicit property override precedence`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <version>1.0.0</version>
        </properties>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)
    val resolved = resolver.resolveProperty("version", resolver.properties(), mapOf("version" to "2.0.0"))

    // then
    assertEquals("2.0.0", resolved)
  }

  @Test
  fun `should resolve project version and direct element lookup`() {
    // given
    val pomXml =
      """
      <project>
        <parent>
          <version>1.0.0</version>
        </parent>
        <artifactId>demo-app</artifactId>
        <version>2.0.0</version>
      </project>
      """.trimIndent()

    // when
    val resolver = PomResolver(pomXml)

    // then
    assertEquals("2.0.0", resolver.projectVersion())
    assertEquals("demo-app", resolver.elementText("artifactId"))
  }
}
