/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry;
import io.camunda.zeebe.qa.util.testcontainers.ProxyRegistry.ContainerProxy;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Use Toxiproxy. Even though it does not support UDP, we can still use Toxiproxy because only
 * "gossip" messages use UDP. SWIM has other messages to probe and sync that uses TCP. So the
 * brokers can still find each other.
 */
@Testcontainers
@ZeebeIntegration
final class AdvertisedAddressTest {
  private static final String TOXIPROXY_IMAGE = "shopify/toxiproxy:2.1.0";

  @Container
  private static final ToxiproxyContainer TOXIPROXY =
      ProxyRegistry.addExposedPorts(new ToxiproxyContainer(DockerImageName.parse(TOXIPROXY_IMAGE)))
          .withAccessToHost(true);

  private static final ProxyRegistry PROXY_REGISTRY = new ProxyRegistry(TOXIPROXY);

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withBrokerConfig(this::configureBroker)
          .withGatewayConfig(this::configureGateway)
          .build();

  /**
   * A beforeEach is needed to rebuild the initial contact points using proxies, something the
   * cluster is unfortunately not aware of.
   */
  @BeforeEach
  void beforeEach() {
    final var contactPoints = proxiedContactPoints();
    cluster
        .brokers()
        .values()
        .forEach(
            b ->
                b.withBrokerConfig(cfg -> cfg.getCluster().setInitialContactPoints(contactPoints)));
    cluster
        .gateways()
        .values()
        .forEach(
            g ->
                g.withGatewayConfig(
                    cfg -> cfg.getCluster().setInitialContactPoints(contactPoints)));
  }

  @Test
  void shouldCommunicateOverProxy() {
    // given
    cluster.start().awaitCompleteTopology();

    // when - send a message to verify the gateway can talk to the broker directly not just via
    // gossip
    try (final var client = cluster.newClientBuilder().build()) {
      final var topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
      final var messageSend =
          client
              .newPublishMessageCommand()
              .messageName("test")
              .correlationKey("test")
              .send()
              .join(5, TimeUnit.SECONDS);

      // then - gateway can talk to the broker
      final var proxiedPorts =
          cluster.brokers().values().stream()
              .map(
                  node ->
                      PROXY_REGISTRY.getOrCreateHostProxy(node.mappedPort(TestZeebePort.COMMAND)))
              .map(ContainerProxy::internalPort)
              .map(TOXIPROXY::getMappedPort)
              .toList();
      TopologyAssert.assertThat(topology)
          .hasClusterSize(3)
          .hasExpectedReplicasCount(1, 3)
          .hasLeaderForEachPartition(1)
          .hasBrokerSatisfying(
              b ->
                  assertThat(b.getAddress())
                      .as("broker 0 advertises the correct proxied address")
                      .isEqualTo(TOXIPROXY.getHost() + ":" + proxiedPorts.get(0)))
          .hasBrokerSatisfying(
              b ->
                  assertThat(b.getAddress())
                      .as("broker 1 advertises the correct proxied address")
                      .isEqualTo(TOXIPROXY.getHost() + ":" + proxiedPorts.get(1)))
          .hasBrokerSatisfying(
              b ->
                  assertThat(b.getAddress())
                      .as("broker 2 advertises the correct proxied address")
                      .isEqualTo(TOXIPROXY.getHost() + ":" + proxiedPorts.get(2)));
      assertThat(messageSend.getMessageKey()).isPositive();
    }
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    final var commandApiProxy =
        PROXY_REGISTRY.getOrCreateHostProxy(broker.mappedPort(TestZeebePort.COMMAND));
    final var internalApiProxy =
        PROXY_REGISTRY.getOrCreateHostProxy(broker.mappedPort(TestZeebePort.CLUSTER));

    broker.withBrokerConfig(
        cfg -> {
          final var network = cfg.getNetwork();
          network.getInternalApi().setAdvertisedHost(TOXIPROXY.getHost());
          network
              .getInternalApi()
              .setAdvertisedPort(TOXIPROXY.getMappedPort(internalApiProxy.internalPort()));
          network.getCommandApi().setAdvertisedHost(TOXIPROXY.getHost());
          network
              .getCommandApi()
              .setAdvertisedPort(TOXIPROXY.getMappedPort(commandApiProxy.internalPort()));

          // Since gossip does not work with Toxiproxy, increase the sync interval so changes are
          // propagated faster
          cfg.getCluster().getMembership().setSyncInterval(Duration.ofMillis(100));
        });
  }

  private void configureGateway(final TestGateway<?> gateway) {
    final var gatewayClusterProxy =
        PROXY_REGISTRY.getOrCreateHostProxy(gateway.mappedPort(TestZeebePort.CLUSTER));

    gateway.withGatewayConfig(
        cfg -> {
          cfg.getCluster()
              .setAdvertisedHost(TOXIPROXY.getHost())
              .setAdvertisedPort(TOXIPROXY.getMappedPort(gatewayClusterProxy.internalPort()));
        });
  }

  private List<String> proxiedContactPoints() {
    final List<String> contactPoints = new ArrayList<>();

    for (final var broker : cluster.brokers().values()) {
      final var internalApiProxy =
          PROXY_REGISTRY.getOrCreateHostProxy(broker.mappedPort(TestZeebePort.CLUSTER));
      contactPoints.add(
          TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(internalApiProxy.internalPort()));
    }

    return contactPoints;
  }
}
