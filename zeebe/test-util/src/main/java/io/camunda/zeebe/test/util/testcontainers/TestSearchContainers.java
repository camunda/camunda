/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.testcontainers;

import java.time.Duration;
import java.util.Objects;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("resource")
public final class TestSearchContainers {
  public static final String CAMUNDA_DATABASE = "camunda";
  public static final String CAMUNDA_USER = "camunda";
  public static final String CAMUNDA_PASSWORD = "Camunda_Pass123!";

  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(
              Objects.requireNonNullElse(
                  org.elasticsearch.client.RestClient.class.getPackage().getImplementationVersion(),
                  "8.19.0"));
  private static final DockerImageName OPENSEARCH_IMAGE =
      DockerImageName.parse("opensearchproject/opensearch").withTag("2.19.0");
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse("postgres").withTag("15.3-alpine");
  private static final DockerImageName MARIADB_IMAGE =
      DockerImageName.parse("mariadb").withTag("11.4");
  private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql").withTag("8.4");
  private static final DockerImageName MSSQLSERVER_IMAGE =
      DockerImageName.parse("mcr.microsoft.com/mssql/server").withTag("2019-latest");
  private static final DockerImageName ORACLE_IMAGE =
      DockerImageName.parse("gvenzl/oracle-free").withTag("slim");

  private TestSearchContainers() {}

  /**
   * Returns an OpenSearch container configured to use 1024mb of heap and 512mb of direct memory.
   * This is required because OpenSearch, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   */
  public static OpenSearchContainer<?> createDefaultOpensearchContainer() {
    return new OpenSearchContainer<>(OPENSEARCH_IMAGE)
        .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms1024m -Xmx1024m -XX:MaxDirectMemorySize=536870912")
        .withEnv("action.destructive_requires_name", "false")
        .withEnv("action.auto_create_index", "true");
  }

  /**
   * Returns an Elasticsearch container pointing at the same version as the {@link
   * org.elasticsearch.client.RestClient}.
   *
   * <p>The container is configured to use 512m of heap and 512m of direct memory. This is required
   * because Elasticsearch 7.x, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   */
  public static ElasticsearchContainer createDefeaultElasticsearchContainer() {
    return createElasticsearchContainer(ELASTIC_IMAGE);
  }

  /**
   * Returns an Elasticsearch container pointing at the same version as the {@link
   * org.elasticsearch.client.RestClient}.
   *
   * <p>The container is configured to use 512m of heap and 512m of direct memory. This is required
   * because Elasticsearch 7.x, by default, will grab all the RAM available otherwise.
   *
   * <p>Additionally, security is explicitly disabled to avoid having tons of warning printed out.
   *
   * @param elasticImage name of the elasticsearch docker image to use
   */
  public static ElasticsearchContainer createElasticsearchContainer(
      final DockerImageName elasticImage) {
    return new ElasticsearchContainer(elasticImage)
        // use JVM option files to avoid overwriting default options set by the ES container class
        .withClasspathResourceMapping(
            "elasticsearch-fast-startup.options",
            "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
            BindMode.READ_ONLY)
        // can be slow in CI
        .withStartupTimeout(Duration.ofMinutes(5))
        .withEnv("action.auto_create_index", "true")
        .withEnv("xpack.security.enabled", "false")
        .withEnv("xpack.watcher.enabled", "false")
        .withEnv("xpack.ml.enabled", "false")
        .withEnv("action.destructive_requires_name", "false");
  }

  public static PostgreSQLContainer<?> createDefaultPostgresContainer() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername(CAMUNDA_USER)
        .withPassword(CAMUNDA_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static PostgreSQLContainer<?> createManualPostgresContainer() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername("admin")
        .withPassword("admin")
        .withInitScripts(
            "db-init-scripts/postgres-manual-user.sql",
            "liquibase/sql/create/postgresql/postgresql_master.sql")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static OracleContainer createDefaultOracleContainer() {
    return new OracleContainer(ORACLE_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername(CAMUNDA_USER)
        .withPassword(CAMUNDA_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  /**
   * For Oracle the creation of a separate user is not supported. In Oracle DBs, user and schema are
   * the same thing and the Camunda SQL scripts to not support a schema prefix out of the box.
   * Therefore, we just use the default user but run the full create script to simulate the manual
   * user setup.
   */
  public static OracleContainer createManualOracleContainer() {
    return new OracleContainer(ORACLE_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername(CAMUNDA_USER)
        .withPassword(CAMUNDA_PASSWORD)
        .withInitScripts("liquibase/sql/create/oracle/oracle_master.sql")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static MariaDBContainer<?> createDefaultMariaDBContainer() {
    return new MariaDBContainer<>(MARIADB_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername(CAMUNDA_USER)
        .withPassword(CAMUNDA_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static MariaDBContainer<?> createManualMariaDBContainer() {
    return new MariaDBContainer<>(MARIADB_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername("admin")
        .withPassword("admin")
        // the created user admin:admin has only restricted privileges and cannot create additional
        // users. So we need to mount the script as root init script into the container
        // use classpath resource mapping to place the init script in the proper docker entrypoint
        .withClasspathResourceMapping(
            "db-init-scripts/mariadb-manual-user.sql",
            "/docker-entrypoint-initdb.d/mariadb-manual-user.sql",
            BindMode.READ_ONLY)
        .withInitScripts("liquibase/sql/create/mariadb/mariadb_master.sql")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static MySQLContainer<?> createDefaultMySQLContainer() {
    return new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername(CAMUNDA_USER)
        .withPassword(CAMUNDA_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static MySQLContainer<?> createManualMySQLContainer() {
    return new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName(CAMUNDA_DATABASE)
        .withUsername("admin")
        .withPassword("admin")
        // the created user admin:admin has only restricted privileges and cannot create additional
        // users. So we need to mount the script as root init script into the container
        // use classpath resource mapping to place the init script in the proper docker entrypoint
        .withClasspathResourceMapping(
            "db-init-scripts/mysql-manual-user.sql",
            "/docker-entrypoint-initdb.d/mysql-manual-user.sql",
            BindMode.READ_ONLY)
        .withInitScripts("liquibase/sql/create/mysql/mysql_master.sql")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  public static MSSQLServerContainer createDefaultMSSQLServerContainer() {
    return new MSSQLServerContainer(MSSQLSERVER_IMAGE)
        .withPassword(CAMUNDA_PASSWORD)
        .withStartupTimeout(Duration.ofMinutes(5))
        .acceptLicense();
  }

  public static MSSQLServerContainer createManualMSSQLServerContainer() {
    return new MSSQLServerContainer(MSSQLSERVER_IMAGE)
        .withInitScripts(
            "db-init-scripts/mssql-manual-user.sql", "liquibase/sql/create/mssql/mssql_master.sql")
        .withStartupTimeout(Duration.ofMinutes(5))
        .acceptLicense();
  }
}
