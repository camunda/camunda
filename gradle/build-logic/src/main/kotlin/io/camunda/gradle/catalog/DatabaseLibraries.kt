package io.camunda.gradle.catalog

import org.gradle.api.initialization.dsl.VersionCatalogBuilder

/**
 * Search, relational and embedded database libraries: Elasticsearch, OpenSearch, JDBC drivers,
 * MyBatis, Liquibase and the matching Testcontainers modules.
 */
internal fun VersionCatalogBuilder.catalogDatabaseLibraries() {
  library("co-elastic-clients-elasticsearch-java", "co.elastic.clients", "elasticsearch-java")
    .versionRef("co-elastic-clients-elasticsearch-java")
  library(
      "co-elastic-clients-elasticsearch-java-optimize",
      "co.elastic.clients",
      "elasticsearch-java",
    )
    .versionRef("optimize-elasticsearch-java-client")
  library(
      "com-github-vertical-blank-sql-formatter",
      "com.github.vertical-blank",
      "sql-formatter",
    )
    .versionRef("com-github-vertical-blank-sql-formatter")
  library("com-h2database-h2", "com.h2database", "h2").versionRef("h2")
  library("com-microsoft-sqlserver-mssql-jdbc", "com.microsoft.sqlserver", "mssql-jdbc")
    .withoutVersion()
  library("com-mysql-mysql-connector-j", "com.mysql", "mysql-connector-j").withoutVersion()
  library("com-oracle-database-jdbc-ojdbc8", "com.oracle.database.jdbc", "ojdbc8").withoutVersion()
  library("com-zaxxer-hikaricp", "com.zaxxer", "HikariCP").versionRef("com-zaxxer-hikaricp")
  library("org-apache-lucene-lucene-core", "org.apache.lucene", "lucene-core").withoutVersion()
  library(
      "org-elasticsearch-client-elasticsearch-rest-client",
      "org.elasticsearch.client",
      "elasticsearch-rest-client",
    )
    .versionRef("elasticsearch")
  library(
      "org-elasticsearch-client-elasticsearch-rest-client-optimize",
      "org.elasticsearch.client",
      "elasticsearch-rest-client",
    )
    .versionRef("optimize-elasticsearch-client")
  // version managed by buildlogic.optimize-conventions
  library("org-elasticsearch-elasticsearch", "org.elasticsearch", "elasticsearch").withoutVersion()
  library("org-liquibase-liquibase-core", "org.liquibase", "liquibase-core").versionRef("liquibase")
  library("org-mariadb-jdbc-mariadb-java-client", "org.mariadb.jdbc", "mariadb-java-client")
    .versionRef("org-mariadb-jdbc-mariadb-java-client")
  library("org-mybatis-mybatis", "org.mybatis", "mybatis").versionRef("mybatis")
  library("org-mybatis-mybatis-spring", "org.mybatis", "mybatis-spring")
    .versionRef("org-mybatis-mybatis-spring")
  library("org-opensearch-client-opensearch-java", "org.opensearch.client", "opensearch-java")
    .versionRef("opensearch-java")
  library(
      "org-opensearch-client-opensearch-rest-client",
      "org.opensearch.client",
      "opensearch-rest-client",
    )
    .versionRef("org-opensearch-client-opensearch-rest-client")
  library(
      "org-opensearch-opensearch-testcontainers",
      "org.opensearch",
      "opensearch-testcontainers",
    )
    .versionRef("org-opensearch-opensearch-testcontainers")
  library("org-postgresql-postgresql", "org.postgresql", "postgresql").versionRef("postgresql")
  library("org-rocksdb-rocksdbjni", "org.rocksdb", "rocksdbjni")
    .versionRef("org-rocksdb-rocksdbjni")
  library(
      "org-testcontainers-testcontainers-elasticsearch",
      "org.testcontainers",
      "testcontainers-elasticsearch",
    )
    .withoutVersion()
  library("org-testcontainers-testcontainers-jdbc", "org.testcontainers", "testcontainers-jdbc")
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-mariadb",
      "org.testcontainers",
      "testcontainers-mariadb",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-mssqlserver",
      "org.testcontainers",
      "testcontainers-mssqlserver",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-mysql",
      "org.testcontainers",
      "testcontainers-mysql",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-oracle-free",
      "org.testcontainers",
      "testcontainers-oracle-free",
    )
    .withoutVersion()
  library(
      "org-testcontainers-testcontainers-postgresql",
      "org.testcontainers",
      "testcontainers-postgresql",
    )
    .withoutVersion()
  library(
      "software-amazon-jdbc-aws-advanced-jdbc-wrapper",
      "software.amazon.jdbc",
      "aws-advanced-jdbc-wrapper",
    )
    .versionRef("software-amazon-jdbc-aws-advanced-jdbc-wrapper")
}
