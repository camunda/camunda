/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.network;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;

import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Tests TLS-secured communication between cluster nodes using container images. This test ensures
 * that:
 *
 * <ul>
 *   <li>The Zeebe gateway port (26500) is secured with TLS
 *   <li>The REST API ports (8080) are secured with TLS
 *   <li>The Zeebe command API port (26501) is secured with TLS
 *   <li>The cluster internal communication ports (26502) are secured with TLS
 * </ul>
 */
@Testcontainers
final class SecuredClusterCommunicationIT {

  private static final SelfSignedCertificate CERTIFICATE = newCertificate();
  private static final String CONTAINER_CERT_PATH = "/tmp/certificate.pem";
  private static final String CONTAINER_KEY_PATH = "/tmp/key.pem";

  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";
  private static final String ES_URL = "http://elastic:9200";

  private static final String OPERATE_IMAGE_NAME =
      Optional.ofNullable(System.getenv("OPERATE_TEST_DOCKER_IMAGE"))
          .orElse("camunda/operate:current-test");
  private static final String TASKLIST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("TASKLIST_TEST_DOCKER_IMAGE"))
          .orElse("camunda/tasklist:current-test");
  private static final String CAMUNDA_IMAGE_NAME =
      Optional.ofNullable(System.getenv("CAMUNDA_TEST_DOCKER_IMAGE"))
          .orElse("camunda/camunda:SNAPSHOT");

  private static final DockerImageName OPERATE = DockerImageName.parse(OPERATE_IMAGE_NAME);
  private static final DockerImageName TASKLIST = DockerImageName.parse(TASKLIST_IMAGE_NAME);
  private static final DockerImageName CAMUNDA = DockerImageName.parse(CAMUNDA_IMAGE_NAME);

  @AutoClose private final Network network = Network.newNetwork();
  private final String testPrefix = UUID.randomUUID().toString();

  @Test
  void shouldSecureAllPortsWithSeparateContainers() {
    // given - separate containers: zeebe with embedded gateway, operate, and tasklist
    final ElasticsearchContainer elasticsearch = createElasticsearchContainer();
    elasticsearch.start();

    final ZeebeContainer zeebe = createZeebeContainer();
    zeebe.start();

    final GenericContainer<?> operate = createOperateContainer(zeebe);
    operate.start();

    final GenericContainer<?> tasklist = createTasklistContainer(zeebe);
    tasklist.start();

    try {
      // then - verify all ports are secured with the certificate

      // Zeebe: REST API (8080), Gateway (26500), Command (26501), Internal (26502)
      assertAddressIsSecured(
          "zeebe-rest", new InetSocketAddress(zeebe.getExternalHost(), zeebe.getMappedPort(8080)));
      assertAddressIsSecured(
          "zeebe-gateway",
          new InetSocketAddress(
              zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.GATEWAY.getPort())));
      assertAddressIsSecured(
          "zeebe-command",
          new InetSocketAddress(
              zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.COMMAND.getPort())));
      assertAddressIsSecured(
          "zeebe-internal",
          new InetSocketAddress(
              zeebe.getExternalHost(), zeebe.getMappedPort(ZeebePort.INTERNAL.getPort())));

      // Operate: Internal port (26502)
      assertAddressIsSecured(
          "operate-internal",
          new InetSocketAddress(
              operate.getHost(), operate.getMappedPort(ZeebePort.INTERNAL.getPort())));

      // Tasklist: Internal port (26502)
      assertAddressIsSecured(
          "tasklist-internal",
          new InetSocketAddress(
              tasklist.getHost(), tasklist.getMappedPort(ZeebePort.INTERNAL.getPort())));
    } finally {
      tasklist.stop();
      operate.stop();
      zeebe.stop();
      elasticsearch.stop();
    }
  }

  @Test
  void shouldSecureAllPortsWithCamundaContainers() {
    // given - camunda/camunda container (single application)
    final ElasticsearchContainer elasticsearch = createElasticsearchContainer();
    elasticsearch.start();

    final GenericContainer<?> camunda = createCamundaContainer();
    camunda.start();

    try {
      // then - verify all ports are secured with the certificate

      // Camunda: REST API (8080), Gateway (26500), Command (26501), Internal (26502)
      assertAddressIsSecured(
          "camunda-rest", new InetSocketAddress(camunda.getHost(), camunda.getMappedPort(8080)));
      assertAddressIsSecured(
          "camunda-gateway",
          new InetSocketAddress(
              camunda.getHost(), camunda.getMappedPort(ZeebePort.GATEWAY.getPort())));
      assertAddressIsSecured(
          "camunda-command",
          new InetSocketAddress(
              camunda.getHost(), camunda.getMappedPort(ZeebePort.COMMAND.getPort())));
      assertAddressIsSecured(
          "camunda-internal",
          new InetSocketAddress(
              camunda.getHost(), camunda.getMappedPort(ZeebePort.INTERNAL.getPort())));
    } finally {
      camunda.stop();
      elasticsearch.stop();
    }
  }

  private ElasticsearchContainer createElasticsearchContainer() {
    return TestSearchContainers.createDefeaultElasticsearchContainer()
        .withNetwork(network)
        .withNetworkAliases("elastic")
        .withStartupTimeout(Duration.ofMinutes(5));
  }

  private ZeebeContainer createZeebeContainer() {
    return new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
        .withNetwork(network)
        .withNetworkAliases("zeebe")
        // TLS for internal/command APIs (broker network security)
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // TLS for gateway API (gRPC)
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // TLS for REST API
        .withEnv("SERVER_SSL_ENABLED", "true")
        .withEnv("SERVER_SSL_CERTIFICATE", CONTAINER_CERT_PATH)
        .withEnv("SERVER_SSL_CERTIFICATEPRIVATEKEY", CONTAINER_KEY_PATH)
        // Copy certificate files
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
            CONTAINER_CERT_PATH)
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), CONTAINER_KEY_PATH)
        // Database configuration
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_DATABASE_URL", ES_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ES_URL)
        // Exporter configuration
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_CLASSNAME", "io.camunda.exporter.CamundaExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_URL", ES_URL)
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_INDEXPREFIX", testPrefix)
        // Security configuration
        .withEnv(UNPROTECTED_API_ENV_VAR, "true")
        // Additional exposed ports
        .withAdditionalExposedPort(8080)
        .withAdditionalExposedPort(ZeebePort.INTERNAL.getPort())
        .withAdditionalExposedPort(ZeebePort.COMMAND.getPort())
        // Disable topology check since client doesn't have TLS config
        .withoutTopologyCheck();
  }

  private GenericContainer<?> createOperateContainer(final ZeebeContainer zeebe) {
    return new GenericContainer<>(OPERATE)
        .withNetwork(network)
        .withNetworkAliases("operate")
        // Cluster security (TLS for internal communication)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", "zeebe:26502")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_ADVERTISEDHOST", "operate")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", "operate")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // Copy certificate files
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
            CONTAINER_CERT_PATH)
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), CONTAINER_KEY_PATH)
        // Database configuration
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_DATABASE_URL", ES_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ES_URL)
        // Zeebe gateway connection (plaintext for simplicity)
        .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebe.getInternalGatewayAddress())
        // Exposed ports
        .withExposedPorts(8080, 9600, ZeebePort.INTERNAL.getPort())
        .waitingFor(
            new HttpWaitStrategy()
                .forPath("/actuator/health/readiness")
                .forPort(9600)
                .withStartupTimeout(Duration.ofMinutes(2)))
        .dependsOn(zeebe);
  }

  private GenericContainer<?> createTasklistContainer(final ZeebeContainer zeebe) {
    return new GenericContainer<>(TASKLIST)
        .withNetwork(network)
        .withNetworkAliases("tasklist")
        .withEnv("SPRING_PROFILES_ACTIVE", "consolidated-auth")
        // Cluster security (TLS for internal communication)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", "zeebe:26502")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_ADVERTISEDHOST", "tasklist")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", "tasklist")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // Copy certificate files
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
            CONTAINER_CERT_PATH)
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), CONTAINER_KEY_PATH)
        // Database configuration
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_DATABASE_URL", ES_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ES_URL)
        // Zeebe gateway connection (plaintext for simplicity)
        .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getInternalGatewayAddress())
        .withEnv(
            "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://" + zeebe.getInternalHost() + ":8080")
        // Exposed ports
        .withExposedPorts(8080, 9600, ZeebePort.INTERNAL.getPort())
        .waitingFor(
            new HttpWaitStrategy()
                .forPath("/actuator/health/readiness")
                .forPort(9600)
                .withStartupTimeout(Duration.ofMinutes(2)))
        .dependsOn(zeebe);
  }

  private GenericContainer<?> createCamundaContainer() {
    return new GenericContainer<>(CAMUNDA)
        .withNetwork(network)
        .withNetworkAliases("camunda")
        // TLS for internal/command APIs (broker network security)
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // TLS for gateway API (gRPC)
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH", CONTAINER_CERT_PATH)
        .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH", CONTAINER_KEY_PATH)
        // TLS for REST API
        .withEnv("SERVER_SSL_ENABLED", "true")
        .withEnv("SERVER_SSL_CERTIFICATE", CONTAINER_CERT_PATH)
        .withEnv("SERVER_SSL_CERTIFICATEPRIVATEKEY", CONTAINER_KEY_PATH)
        // Copy certificate files
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
            CONTAINER_CERT_PATH)
        .withCopyToContainer(
            MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), CONTAINER_KEY_PATH)
        // Database configuration
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_DATABASE_URL", ES_URL)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", ES_URL)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", ES_URL)
        // Exporter configuration
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_CLASSNAME", "io.camunda.exporter.CamundaExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_URL", ES_URL)
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_INDEXPREFIX", testPrefix)
        // Security configuration
        .withEnv(UNPROTECTED_API_ENV_VAR, "true")
        // Enable embedded gateway
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
        // Schema creation
        .withEnv(CREATE_SCHEMA_ENV_VAR, "true")
        // Exposed ports
        .withExposedPorts(
            8080,
            9600,
            ZeebePort.GATEWAY.getPort(),
            ZeebePort.COMMAND.getPort(),
            ZeebePort.INTERNAL.getPort())
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(9600)
                .forPath("/actuator/health/readiness")
                .withStartupTimeout(Duration.ofMinutes(5)));
  }

  private void assertAddressIsSecured(final Object nodeId, final SocketAddress address) {
    SslAssert.assertThat(address)
        .as("node %s is not secured correctly at address %s", nodeId, address)
        .isSecuredBy(CERTIFICATE);
  }

  private static SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }
}
