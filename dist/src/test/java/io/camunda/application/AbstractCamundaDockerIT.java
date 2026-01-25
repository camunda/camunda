/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.fail;

import com.github.dockerjava.api.DockerClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCamundaDockerIT {

  protected static final int SERVER_PORT = 8080;
  protected static final int MANAGEMENT_PORT = 9600;
  protected static final int GATEWAY_GRPC_PORT = 26500;
  protected static final int ELASTICSEARCH_PORT = 9200;
  protected static final String CAMUNDA_NETWORK_ALIAS = "camunda";
  protected static final String ELASTICSEARCH_NETWORK_ALIAS = "elasticsearch";
  protected static final String POSTGRES_NETWORK_ALIAS = "postgresql";
  protected static final String ORACLE_NETWORK_ALIAS = "oracle";
  protected static final String MYSQL_NETWORK_ALIAS = "mysql";
  protected static final String DATABASE_TYPE = "elasticsearch";
  protected static final String CAMUNDA_TEST_DOCKER_IMAGE =
      System.getProperty("camunda.docker.test.image", "camunda/camunda:SNAPSHOT");
  protected static final String ELASTICSEARCH_DOCKER_IMAGE =
      System.getProperty(
          "camunda.docker.test.elasticsearch.image",
          "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION);
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamundaDockerIT.class);
  private static final DockerClient DOCKER_CLIENT = DockerClientFactory.lazyClient();
  protected Network network;
  private final List<GenericContainer<?>> createdContainers = new ArrayList<>();
  private final List<String> imagesToRemove = new ArrayList<>();

  @BeforeEach
  public void beforeEach() {
    network = Network.newNetwork();
  }

  @AfterEach
  public void stopContainers() {
    createdContainers.forEach(GenericContainer::stop);
    network.close();
    network = null;
  }

  @AfterAll
  public void removeImages() {
    imagesToRemove.stream()
        .distinct()
        .forEach(image -> DOCKER_CLIENT.removeImageCmd(image).withForce(true).exec());
  }

  protected void startContainer(final GenericContainer<?> container) {
    try {
      container.start();
    } catch (final Exception e) {
      fail(
          String.format(
              "Failed to start container.\n" + "Exception message: %s.\n" + "Container Logs:\n%s",
              e.getMessage(), container.getLogs()));
    }
  }

  protected <T extends GenericContainer<?>> T createContainer(final Supplier<T> containerSupplier) {
    final T container = containerSupplier.get();
    createdContainers.add(container);
    return container;
  }

  protected ElasticsearchContainer createElasticsearchContainer() {
    return TestSearchContainers.createElasticsearchContainer(
            DockerImageName.parse(ELASTICSEARCH_DOCKER_IMAGE))
        .withNetwork(network)
        .withNetworkAliases(ELASTICSEARCH_NETWORK_ALIAS)
        .withExposedPorts(ELASTICSEARCH_PORT);
  }

  protected PostgreSQLContainer<?> createPostgresContainer() {
    final var container =
        TestSearchContainers.createDefaultPostgresContainer()
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withExposedPorts(PostgreSQLContainer.POSTGRESQL_PORT);
    imagesToRemove.add(container.getDockerImageName());
    return container;
  }

  protected OracleContainer createOracleContainer() {
    final var container =
        TestSearchContainers.createDefaultOracleContainer()
            .withNetwork(network)
            .withNetworkAliases(ORACLE_NETWORK_ALIAS)
            .withExposedPorts(1521);
    imagesToRemove.add(container.getDockerImageName());
    return container;
  }

  protected MySQLContainer<?> createMysqlContainer() {
    final var container =
        TestSearchContainers.createDefaultMySQLContainer()
            .withNetwork(network)
            .withNetworkAliases(MYSQL_NETWORK_ALIAS)
            .withExposedPorts(3306);
    imagesToRemove.add(container.getDockerImageName());
    return container;
  }

  protected GenericContainer<?> createUnauthenticatedUnifiedConfigCamundaContainer() {
    return new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
        .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
        .withNetwork(network)
        .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(MANAGEMENT_PORT)
                .forPath("/actuator/health")
                .withReadTimeout(Duration.ofSeconds(120)))
        .withStartupTimeout(Duration.ofSeconds(300))
        // Unified Configuration
        .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_DISK_FREESPACE_PROCESSING", "512MB")
        .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_DISK_FREESPACE_REPLICATION", "200MB")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DATABASE_TYPE)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", elasticsearchUrl())
        // ---
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
  }

  private GenericContainer<?> createUnauthenticatedUnifiedConfigCamundaContainerWithRdbms(
      final String url, final String vendorId, final String springProfile) {
    return new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
        .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
        .withNetwork(network)
        .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(MANAGEMENT_PORT)
                .forPath("/actuator/health")
                .withReadTimeout(Duration.ofSeconds(120)))
        .withStartupTimeout(Duration.ofSeconds(420))
        // Unified Configuration
        .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_DISK_FREESPACE_PROCESSING", "512MB")
        .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_DISK_FREESPACE_REPLICATION", "200MB")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "rdbms")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_URL", url)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_USERNAME", "camunda")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_PASSWORD", "camunda")
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_RDBMS_DATABASE_VENDOR_ID", vendorId)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_RDBMS_CLASSNAME", "io.camunda.exporter.rdbms.RdbmsExporter")
        // ---
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTED_API", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
        .withEnv("SPRING_PROFILES_ACTIVE", springProfile);
  }

  protected GenericContainer<?> createUnauthenticatedUnifiedConfigCamundaContainerWithPostgres() {
    return createUnauthenticatedUnifiedConfigCamundaContainerWithRdbms(
        "jdbc:postgresql://postgresql:5432/camunda", "postgresql", "rdbmsPostgres,broker,insecure");
  }

  protected GenericContainer<?> createUnauthenticatedUnifiedConfigCamundaContainerWithOracle() {
    return createUnauthenticatedUnifiedConfigCamundaContainerWithRdbms(
        "jdbc:oracle:thin:@//oracle:1521/camunda", "oracle", "rdbmsOracle,broker,insecure");
  }

  protected GenericContainer<?> createUnauthenticatedUnifiedConfigCamundaContainerWithMysql() {
    return createUnauthenticatedUnifiedConfigCamundaContainerWithRdbms(
        "jdbc:mysql://mysql:3306/camunda", "mysql", "rdbmsMysql,broker,insecure");
  }

  protected GenericContainer<?> createCamundaContainer() {
    return new GenericContainer<>(CAMUNDA_TEST_DOCKER_IMAGE)
        .withLogConsumer(new Slf4jLogConsumer(LOGGER))
        .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
        .withNetwork(network)
        .withNetworkAliases(CAMUNDA_NETWORK_ALIAS)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(MANAGEMENT_PORT)
                .forPath("/actuator/health")
                .withReadTimeout(Duration.ofSeconds(120)))
        .withStartupTimeout(Duration.ofSeconds(300))
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", elasticsearchUrl())
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", gatewayAddress())
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", httpUrl())
        // Unified Configuration
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", elasticsearchUrl())
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DATABASE_TYPE)
        // ---
        .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", gatewayAddress())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
  }

  protected static String httpUrl() {
    return String.format("http://%s:%d", CAMUNDA_NETWORK_ALIAS, SERVER_PORT);
  }

  protected static String gatewayAddress() {
    return String.format("%s:%d", CAMUNDA_NETWORK_ALIAS, GATEWAY_GRPC_PORT);
  }

  protected static String elasticsearchUrl() {
    return String.format("http://%s:%d", ELASTICSEARCH_NETWORK_ALIAS, ELASTICSEARCH_PORT);
  }

  protected static String postgresUrl() {
    return "jdbc:postgresql://postgresql:5432/postgres";
  }
}
