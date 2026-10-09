/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.network;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.CamundaPort;
import io.camunda.container.cluster.ClusterNode;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Verifies that a cluster made up of {@code camunda/camunda} Docker containers (the image built
 * from {@code camunda.Dockerfile}) can be configured to secure all of its communication with TLS.
 *
 * <p>This complements the in-JVM {@code SecureClusteredMessagingIT}: since the TLS implementation
 * relies on native libraries, only a test running the real image proves that the production
 * classpath can actually serve TLS on the REST, gRPC, command and internal ports.
 */
@Testcontainers
final class SecuredContainerClusterCommunicationIT {
  private static final String CERTIFICATE_FQDN = "cluster.camunda.local";
  private static final SelfSignedCertificate CERTIFICATE = newCertificate();
  private static final String CERTIFICATE_PATH = "/usr/local/camunda/certificate.pem";
  private static final String PRIVATE_KEY_PATH = "/usr/local/camunda/private.key";
  private static final int CLUSTER_SIZE = 2;
  // dropping the topology check also resets the startup timeout to its one minute default, which is
  // tight for two nodes booting in parallel
  private static final Duration NODE_STARTUP_TIMEOUT = Duration.ofMinutes(2);

  @Container
  private final CamundaCluster cluster =
      CamundaCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withBrokersCount(CLUSTER_SIZE)
          .withPartitionsCount(1)
          .withReplicationFactor(CLUSTER_SIZE)
          .withEmbeddedGateway(true)
          .withNodeConfig(SecuredContainerClusterCommunicationIT::secureNode)
          // the topology check uses a plaintext client, which cannot talk to the secured gateway;
          // the test asserts the topology itself with a client configured for TLS
          .withGatewayConfig(
              gateway -> gateway.withoutTopologyCheck().withStartupTimeout(NODE_STARTUP_TIMEOUT))
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  private final ContainerLogsDumper logsDumper = new ContainerLogsDumper(cluster::getNodes);

  @Test
  void shouldFormClusterWithAllPortsSecured() {
    // given - a cluster of Camunda containers with TLS enabled on all ports

    // when - the nodes can only form a cluster if the command and internal APIs speak TLS
    try (final var client = newSecureClient()) {
      Awaitility.await("until the topology is complete")
          .atMost(NODE_STARTUP_TIMEOUT)
          .untilAsserted(
              () ->
                  TopologyAssert.assertThat(
                          client.newTopologyRequest().send().join(5, TimeUnit.SECONDS))
                      .isComplete(CLUSTER_SIZE, 1, CLUSTER_SIZE));
    }

    // then
    cluster.getNodes().forEach(SecuredContainerClusterCommunicationIT::assertNodeIsSecured);
  }

  private CamundaClient newSecureClient() {
    final var gateway = cluster.getAvailableGateway();

    return CamundaClient.newClientBuilder()
        .grpcAddress(gateway.getGrpcAddress("https"))
        .restAddress(gateway.getRestAddress("https"))
        .preferRestOverGrpc(false)
        .caCertificatePath(CERTIFICATE.certificate().getAbsolutePath())
        // the self signed certificate is issued for CERTIFICATE_FQDN, not for the mapped host
        .overrideAuthority(CERTIFICATE_FQDN)
        .defaultRequestTimeout(Duration.ofSeconds(5))
        .build();
  }

  private static void secureNode(final ClusterNode<?> node) {
    node.withCopyFileToContainer(
            MountableFile.forHostPath(CERTIFICATE.certificate().toPath(), 0777), CERTIFICATE_PATH)
        .withCopyFileToContainer(
            MountableFile.forHostPath(CERTIFICATE.privateKey().toPath(), 0777), PRIVATE_KEY_PATH)
        .withUnifiedConfig(
            cfg -> {
              // secures the command and internal APIs
              final var transportCluster =
                  cfg.getSecurity().getTransportLayerSecurity().getCluster();
              transportCluster.setEnabled(true);
              transportCluster.setCertificateChainPath(new File(CERTIFICATE_PATH));
              transportCluster.setCertificatePrivateKeyPath(new File(PRIVATE_KEY_PATH));

              // secures the gateway's gRPC API
              final var grpc = cfg.getApi().getGrpc().getSsl();
              grpc.setEnabled(true);
              grpc.setCertificate(new File(CERTIFICATE_PATH));
              grpc.setCertificatePrivateKey(new File(PRIVATE_KEY_PATH));

              cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
            });

    // secures the REST API; there is no unified configuration for it yet
    node.addEnv("SERVER_SSL_ENABLED", "true");
    node.addEnv("SERVER_SSL_CERTIFICATE", CERTIFICATE_PATH);
    node.addEnv("SERVER_SSL_CERTIFICATEPRIVATEKEY", PRIVATE_KEY_PATH);

    node.addEnv(CREATE_SCHEMA_ENV_VAR, "false");
    node.addEnv(UNPROTECTED_API_ENV_VAR, "true");
    node.addEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false");
  }

  private static void assertNodeIsSecured(final String memberId, final ClusterNode<?> node) {
    for (final var port :
        new CamundaPort[] {
          CamundaPort.GATEWAY_REST,
          CamundaPort.GATEWAY_GRPC,
          CamundaPort.COMMAND,
          CamundaPort.INTERNAL
        }) {
      final var address =
          new InetSocketAddress(node.getExternalHost(), node.getMappedPort(port.getPort()));
      SslAssert.assertThat(address)
          .as("port %s of node %s is not secured correctly at address %s", port, memberId, address)
          .isSecuredBy(CERTIFICATE);
    }
  }

  private static SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate(CERTIFICATE_FQDN);
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }
}
