package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/**
 * Version aliases pinned directly from `parent/pom.xml` dependency management, kept separate so
 * that the parent POM surface stays auditable in one place.
 */
internal fun VersionCatalogBuilder.catalogParentVersions(
  pomVersion: (String) -> String,
  optimizePomVersion: (String) -> String,
) {
  // Keep all dependency and tool version properties from parent/pom.xml pinned in the Gradle
  // catalog. Maven-only plugin versions are intentionally excluded.
  version("parent-auth0-commons", pomVersion("version.auth0.commons"))
  version("parent-auth0-jwt", pomVersion("version.auth0.jwt"))
  version("parent-checkstyle", pomVersion("version.checkstyle"))
  version("parent-commons-beanutils", pomVersion("version.commons-beanutils"))
  version("parent-conscrypt", pomVersion("version.conscrypt"))
  version("parent-elasticsearch-container", pomVersion("version.elasticsearch.container"))
  version(
    "parent-elasticsearch-test-container",
    pomVersion("version.elasticsearch-test-container"),
  )
  version("parent-findbugs-annotations", pomVersion("version.findbugs-annotations"))
  version("parent-google-http-client", pomVersion("version.google-http-client"))
  version("parent-guava-annotations", pomVersion("version.guava.annotations"))
  version("parent-hdr-histogram", pomVersion("version.hdr-histogram"))
  version("parent-jakarta-activation", pomVersion("version.jakarta-activation"))
  version("parent-jakarta-json-api", pomVersion("version.jakarta.json-api"))
  version("parent-jopt-simple", pomVersion("version.jopt-simple"))
  version("parent-json-smart", pomVersion("version.json-smart"))
  version("parent-jsr305", pomVersion("version.jsr305"))
  version("parent-lz4", pomVersion("version.lz4"))
  version("parent-model", pomVersion("version.model"))
  version(
    "parent-mybatis-spring-boot-starter",
    pomVersion("version.mybatis-spring-boot-starter"),
  )
  version("parent-node", pomVersion("version.node"))
  version("parent-npm", pomVersion("version.npm"))
  version("parent-opensearch-container", pomVersion("version.opensearch.container"))
  version("parent-osgi", pomVersion("version.osgi"))
  version(
    "parent-postgres-test-container",
    pomVersion("version.postgres-test-container"),
  )
  version(
    "parent-postgressql-testcontainer",
    pomVersion("version.postgressql-testcontainer"),
  )
  version("parent-revapi", pomVersion("version.revapi"))
  version("parent-servlet-api", pomVersion("version.servlet-api"))
  version("parent-yarn", pomVersion("version.yarn"))

  version("optimize-zeebe-docker", optimizePomVersion("zeebe.docker.version"))
  version("optimize-database-type", optimizePomVersion("database.type"))
}
