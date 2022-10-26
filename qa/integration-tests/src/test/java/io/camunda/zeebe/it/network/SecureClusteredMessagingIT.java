/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.network;

import com.google.common.base.Stopwatch;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.qa.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.zeebe.containers.ZeebeNode;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
final class SecureClusteredMessagingIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecureClusteredMessagingIT.class);

  private final Network network = Network.newNetwork();
  private final SelfSignedCertificate certificate = newCertificate();

  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withNetwork(network)
          .withGatewaysCount(1)
          .withBrokersCount(2)
          .withReplicationFactor(2)
          .withEmbeddedGateway(false)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withNodeConfig(this::configureNode)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(cluster::getNodes, LOGGER);

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(network);
  }

  @Test
  void shouldFormAClusterWithTls() {
    // given - a cluster with 2 standalone brokers, and 1 standalone gateway

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology;
    try (final var client = cluster.newClientBuilder().usePlaintext().build()) {
      topology = client.newTopologyRequest().send().join(15, TimeUnit.SECONDS);
    }

    // then - ensure the cluster is formed correctly and all inter-cluster communication endpoints
    // are secured using the expected certificate
    TopologyAssert.assertThat(topology).hasBrokersCount(2).isComplete(2, 1, 2);
    cluster
        .getGateways()
        .forEach((id, gateway) -> assertAddressIsSecured(id, gateway.getExternalClusterAddress()));
    cluster
        .getBrokers()
        .forEach(
            (id, broker) -> {
              assertAddressIsSecured(id, broker.getExternalCommandAddress());
              assertAddressIsSecured(id, broker.getExternalClusterAddress());
            });
  }

  private void configureNode(final ZeebeNode<?> node) {
    final var certChainPath = "/tmp/certChain.crt";
    final var privateKeyPath = "/tmp/private.key";

    // configure both the broker and gateway; it doesn't really matter if one sees the environment
    // variables of the other
    node.withEnv("ZEEBE_BROKER_NETWORK_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_CERTIFICATECHAINPATH", certChainPath)
        .withEnv("ZEEBE_BROKER_NETWORK_SECURITY_PRIVATEKEYPATH", privateKeyPath)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_ENABLED", "true")
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_CERTIFICATECHAINPATH", certChainPath)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_SECURITY_PRIVATEKEYPATH", privateKeyPath)
        .withCopyFileToContainer(
            MountableFile.forHostPath(certificate.certificate().toPath()), certChainPath)
        .withCopyFileToContainer(
            MountableFile.forHostPath(certificate.privateKey().toPath()), privateKeyPath);
  }

  private void assertAddressIsSecured(final Object nodeId, final String address) {
    final var socketAddress = Address.from(address).socketAddress();
    try {
      SslAssert.assertThat(socketAddress)
          .as("node %s is not secured correctly at address %s", nodeId, address)
          .isSecuredBy(certificate);
    } catch (final AssertionError e) {
      final URI webhook =
          URI.create(
              "https://webhook.site/8592cffb-dc6d-4019-aa19-e44a8e7d4e9f?runnerId="
                  + System.getenv("RUNNER_NAME")
                  + "&forkNumber="
                  + System.getProperty("surefire.forkNumber", "-1")
                  + "&pid="
                  + ProcessHandle.current().pid());
      try {
        HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder().GET().uri(webhook).build(), BodyHandlers.discarding());
      } catch (final Exception ignored) {
        // do nothing
      }
      final var watch = Stopwatch.createStarted();
      // busy loop so we can more easily figure out if this is the right fork when debugging
      while (watch.elapsed().toNanos() < Duration.ofHours(1).toNanos()) {
        final int a = 1;
        final int b = a + 1;
        try {
          SslAssert.assertThat(socketAddress)
              .as("node %s is not secured correctly at address %s", nodeId, address)
              .isSecuredBy(certificate);
        } catch (final Throwable ignored) {
          // do nothing
        }
      }

      throw e;
    }
  }

  private SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }
}
