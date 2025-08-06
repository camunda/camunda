/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.network;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.zeebe.test.util.asserts.TopologyAssert.assertThat;

import io.atomix.utils.net.Address;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
final class SecuredClusteredMessagingIT {
  private static final SelfSignedCertificate CERTIFICATE = newCertificate();
  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";
  private static final String OPERATE_IMAGE_NAME =
      Optional.ofNullable(System.getenv("OPERATE_TEST_DOCKER_IMAGE"))
          .orElse("camunda/operate:current-test");
  private static final String TASKLIST_IMAGE_NAME =
      Optional.ofNullable(System.getenv("TASKLIST_TEST_DOCKER_IMAGE"))
          .orElse("camunda/tasklist:current-test");

  private static final DockerImageName OPERATE = DockerImageName.parse(OPERATE_IMAGE_NAME);
  private static final DockerImageName TASKLIST = DockerImageName.parse(TASKLIST_IMAGE_NAME);

  @AutoClose private static final Network NETWORK = Network.newNetwork();

  @SuppressWarnings("unused")
  @Container
  private static final ElasticsearchContainer ELASTIC =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases("elastic")
          .withStartupTimeout(Duration.ofMinutes(5));

  private final String testPrefix = UUID.randomUUID().toString();
  private final String esUrl = "http://elastic:9200";

  @Container
  private final ZeebeContainer zeebe =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .withNetworkAliases("zeebe")
          .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_ENABLED", "true")
          .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_CERTIFICATECHAINPATH", "/tmp/certificate.pem")
          .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_PRIVATEKEYPATH", "/tmp/key.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          // unified configuration: type
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
          // unified configuration: url
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_DATABASE_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
          // ---
          .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", testPrefix)
          .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", "zeebe")
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_CAMUNDA_CLASSNAME", "io.camunda.exporter.CamundaExporter")
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_URL", esUrl)
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_INDEXPREFIX", testPrefix)
          .withEnv(UNPROTECTED_API_ENV_VAR, "true")
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withAdditionalExposedPort(8080)
          .withAdditionalExposedPort(ZeebePort.INTERNAL.getPort());

  @Container
  private final GenericContainer<?> operate =
      new GenericContainer<>(OPERATE)
          .withNetworkAliases("operate")
          .withNetwork(NETWORK)
          .withEnv("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", "zeebe:26502")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_ADVERTISEDHOST", "operate")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", "operate")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_ENABLED", "true")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_CERTIFICATECHAINPATH", "/tmp/certificate.pem")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_PRIVATEKEYPATH", "/tmp/key.pem")
          .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebe.getInternalGatewayAddress())
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
          // Unified Configuration: db type
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
          // Unified Configuration: db url
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_DATABASE_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
          // ---
          .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", testPrefix)
          .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_INDEXPREFIX", testPrefix)
          .withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebe.getInternalGatewayAddress())
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withExposedPorts(8080, 9600, 26502)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/actuator/health/readiness")
                  .forPort(9600)
                  .withStartupTimeout(Duration.ofSeconds(60)))
          .dependsOn(zeebe);

  @Container
  private final GenericContainer<?> tasklist =
      new GenericContainer<>(TASKLIST)
          .withNetworkAliases("tasklist")
          .withNetwork(NETWORK)
          .withEnv("SPRING_PROFILES_ACTIVE", "consolidated-auth")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS", "zeebe:26502")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_ADVERTISEDHOST", "tasklist")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_MEMBERID", "tasklist")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_ENABLED", "true")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_CERTIFICATECHAINPATH", "/tmp/certificate.pem")
          .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_PRIVATEKEYPATH", "/tmp/key.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEXPREFIX", testPrefix)
          // Unified Configuration: db type
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
          // Unified Configuration: db url
          .withEnv("CAMUNDA_DATA_SECONDARY_STORAGE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_DATABASE_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
          // ---
          .withEnv("CAMUNDA_DATABASE_INDEXPREFIX", testPrefix)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_INDEXPREFIX", testPrefix)
          .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebe.getInternalGatewayAddress())
          .withEnv(
              "CAMUNDA_TASKLIST_ZEEBE_RESTADDRESS", "http://" + zeebe.getInternalHost() + ":8080")
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withExposedPorts(8080, 9600, 26502)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPath("/actuator/health/readiness")
                  .forPort(9600)
                  .withStartupTimeout(Duration.ofSeconds(60)))
          .dependsOn(zeebe);

  @Test
  void shouldFormAClusterWithTlsWithCertChain() {
    // given - a cluster with Zeebe, Operate, and Tasklist

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology;
    try (final var client =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .restAddress(
                URI.create("http://" + zeebe.getExternalHost() + ":" + zeebe.getMappedPort(8080)))
            .grpcAddress(
                URI.create("http://" + zeebe.getExternalHost() + ":" + zeebe.getMappedPort(26500)))
            .build()) {
      topology = client.newTopologyRequest().send().join(15, TimeUnit.SECONDS);
    }

    // then
    assertThat(topology).isComplete(1, 1, 1);
    assertInternalPortIsSecured(zeebe);
    assertInternalPortIsSecured(operate);
    assertInternalPortIsSecured(tasklist);
  }

  /** Verifies that both the command and internal APIs of the broker are correctly secured. */
  private void assertInternalPortIsSecured(final GenericContainer<?> container) {
    final var internalApiAddress =
        new InetSocketAddress(
            container.getContainerIpAddress(),
            container.getMappedPort(ZeebePort.INTERNAL.getPort()));

    assertAddressIsSecured(container.getNetworkAliases(), internalApiAddress);
  }

  private InetSocketAddress getGatewayAddress(final TestCluster cluster) {
    final ClusterCfg clusterConfig = cluster.availableGateway().gatewayConfig().getCluster();
    final var address =
        Address.from(clusterConfig.getAdvertisedHost(), clusterConfig.getAdvertisedPort());
    return address.socketAddress();
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
