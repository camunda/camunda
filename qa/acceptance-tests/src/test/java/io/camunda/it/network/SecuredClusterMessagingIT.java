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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
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
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
final class SecuredClusteredMessagingIT {
  private static final SelfSignedCertificate CERTIFICATE = newCertificate();
  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";
  private static final String CAMUNDA_IMAGE_NAME =
      Optional.ofNullable(System.getenv("CAMUNDA_TEST_DOCKER_IMAGE"))
          .orElse("camunda/camunda:current-test");

  private static final DockerImageName CAMUNDA_IMAGE = DockerImageName.parse(CAMUNDA_IMAGE_NAME);

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
  private final ZeebeContainer camunda =
      new ZeebeContainer(CAMUNDA_IMAGE)
          .withNetwork(NETWORK)
          .withNetworkAliases("camunda")
          .withEnv("CAMUNDA_SECURITY_TRANSPORTLAYERSECURITY_CLUSTER_ENABLED", "true")
          .withEnv(
              "CAMUNDA_SECURITY_TRANSPORTLAYERSECURITY_CLUSTER_CERTIFICATECHAINPATH",
              "/tmp/certificate.pem")
          .withEnv(
              "CAMUNDA_SECURITY_TRANSPORTLAYERSECURITY_CLUSTER_CERTIFICATEPRIVATEKEYPATH",
              "/tmp/key.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777),
              "/tmp/certificate.pem")
          .withCopyToContainer(
              MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), "/tmp/key.pem")
          // unified configuration: index prefix
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", testPrefix)
          // unified configuration: type
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_DATABASE_TYPE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_OPERATE_DATABASE", DB_TYPE_ELASTICSEARCH)
          .withEnv("CAMUNDA_TASKLIST_DATABASE", DB_TYPE_ELASTICSEARCH)
          // unified configuration: url
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_DATABASE_URL", esUrl)
          .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_CAMUNDA_CLASSNAME", "io.camunda.exporter.CamundaExporter")
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_URL", esUrl)
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDA_ARGS_CONNECT_INDEXPREFIX", testPrefix)
          .withEnv(UNPROTECTED_API_ENV_VAR, "true")
          .withEnv("CAMUNDA_LOG_LEVEL", "DEBUG")
          .withAdditionalExposedPort(8080)
          .withAdditionalExposedPort(ZeebePort.INTERNAL.getPort());

  @Test
  void shouldFormAClusterWithTlsWithCertChain() {
    // given - a standalone camunda node with cluster TLS enabled

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology;
    try (final var client =
        CamundaClient.newClientBuilder()
            .restAddress(
                URI.create(
                    "http://" + camunda.getExternalHost() + ":" + camunda.getMappedPort(8080)))
            .grpcAddress(
                URI.create(
                    "http://" + camunda.getExternalHost() + ":" + camunda.getMappedPort(26500)))
            .build()) {
      topology = client.newTopologyRequest().send().join(15, TimeUnit.SECONDS);
    }

    // then
    assertThat(topology).isComplete(1, 1, 1);
    assertInternalPortIsSecured(camunda);
  }

  /** Verifies that the broker's internal cluster API is correctly secured with TLS. */
  private void assertInternalPortIsSecured(final ZeebeContainer container) {
    final var internalApiAddress =
        new InetSocketAddress(
            container.getContainerIpAddress(),
            container.getMappedPort(ZeebePort.INTERNAL.getPort()));

    assertAddressIsSecured(container.getNetworkAliases(), internalApiAddress);
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
