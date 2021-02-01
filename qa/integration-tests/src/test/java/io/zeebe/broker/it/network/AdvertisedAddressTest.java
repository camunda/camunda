/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebeGatewayContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeTopologyWaitStrategy;
import io.zeebe.test.util.asserts.TopologyAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;

/**
 * Use ToxiProxy. Even though it does not support UDP, we can still use ToxiProxy because only
 * "gossip" messages use UDP. SWIM has other messages to probe and sync that uses TCP. So the
 * brokers can still find each other.
 */
public class AdvertisedAddressTest {
  private static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";
  private static final String TOXIPROXY_IMAGE = "shopify/toxiproxy:2.1.0";
  private static final int CLUSTER_SIZE = 3;
  private static final int PARTITION_COUNT = 1;
  private static final int REPLICATION_FACTOR = 3;
  private static final String ZEEBE_IMAGE_VERSION = "camunda/zeebe:current-test";
  @Rule public final Network network = Network.newNetwork();

  @Rule
  public final ToxiproxyContainer toxiproxy =
      new ToxiproxyContainer(TOXIPROXY_IMAGE)
          .withNetwork(network)
          .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);

  private List<ZeebeBrokerContainer> containers;
  private List<String> initialContactPoints;
  private ZeebeGatewayContainer gateway;

  @Before
  public void setup() {
    initialContactPoints = new ArrayList<>();
    containers =
        IntStream.range(0, CLUSTER_SIZE)
            .mapToObj(i -> new ZeebeBrokerContainer(ZEEBE_IMAGE_VERSION))
            .collect(Collectors.toList());

    gateway = new ZeebeGatewayContainer(ZEEBE_IMAGE_VERSION);
    IntStream.range(0, CLUSTER_SIZE).forEach(i -> configureBrokerContainer(i, containers));
    configureGatewayContainer(gateway, initialContactPoints.get(0));
  }

  @After
  public void tearDown() {
    containers.parallelStream().forEach(GenericContainer::stop);
  }

  @Test
  public void shouldCommunicateOverProxy() {
    // given
    containers.parallelStream().forEach(GenericContainer::start);
    gateway.start();

    // when
    final ZeebeClientBuilder zeebeClientBuilder =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress(gateway.getExternalGatewayAddress());
    final Topology topology;
    try (final var client = zeebeClientBuilder.build()) {
      topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
      // then - can find each other
      TopologyAssert.assertThat(topology).isComplete(3, 1);

      // when
      final var messageSend =
          client
              .newPublishMessageCommand()
              .messageName("test")
              .correlationKey("test")
              .send()
              .join(5, TimeUnit.SECONDS);
      // then - gateway can talk to the broker
      assertThat(messageSend.getMessageKey()).isPositive();
    }
  }

  private void configureBrokerContainer(final int index, final List<ZeebeBrokerContainer> brokers) {
    final int clusterSize = brokers.size();
    final var broker = brokers.get(index);
    final var hostName = "broker-" + index;
    final var commandApiProxy = toxiproxy.getProxy(hostName, ZeebePort.COMMAND.getPort());
    final var internalApiProxy = toxiproxy.getProxy(hostName, ZeebePort.INTERNAL.getPort());
    final var monitoringApiProxy = toxiproxy.getProxy(hostName, ZeebePort.MONITORING.getPort());

    initialContactPoints.add(
        TOXIPROXY_NETWORK_ALIAS + ":" + internalApiProxy.getOriginalProxyPort());

    LoggerFactory.getLogger("Test")
        .info("Configuring broker with initial contactpoints {}", initialContactPoints);

    broker
        .withNetwork(network)
        .withNetworkAliases(hostName)
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(REPLICATION_FACTOR))
        .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONCOUNT", String.valueOf(PARTITION_COUNT))
        .withEnv(
            "ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", String.join(",", initialContactPoints))
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        .withEnv("ATOMIX_LOG_LEVEL", "INFO")
        .withEnv("ZEEBE_BROKER_NETWORK_COMMANDAPI_ADVERTISEDHOST", TOXIPROXY_NETWORK_ALIAS)
        .withEnv(
            "ZEEBE_BROKER_NETWORK_COMMANDAPI_ADVERTISEDPORT",
            String.valueOf(commandApiProxy.getOriginalProxyPort()))
        .withEnv("ZEEBE_BROKER_NETWORK_INTERNALAPI_ADVERTISEDHOST", TOXIPROXY_NETWORK_ALIAS)
        .withEnv(
            "ZEEBE_BROKER_NETWORK_INTERNALAPI_ADVERTISEDPORT",
            String.valueOf(internalApiProxy.getOriginalProxyPort()))
        .withEnv("ZEEBE_BROKER_NETWORK_MONITORINGAPI_ADVERTISEDHOST", TOXIPROXY_NETWORK_ALIAS)
        .withEnv(
            "ZEEBE_BROKER_NETWORK_MONITORINGAPI_ADVERTISEDPORT",
            String.valueOf(monitoringApiProxy.getOriginalProxyPort()))
        // Since gossip does not work with ToxiProxy, increase the sync interval so changes are
        // propagated faster
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SYNCINTERVAL", "100ms");
  }

  private void configureGatewayContainer(
      final ZeebeGatewayContainer gateway, final String initialContactPoint) {
    // gateway is not behind proxy
    gateway
        .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", initialContactPoint)
        .withTopologyCheck(
            new ZeebeTopologyWaitStrategy()
                .forBrokersCount(CLUSTER_SIZE)
                .forPartitionsCount(PARTITION_COUNT)
                .forReplicationFactor(REPLICATION_FACTOR))
        .withNetwork(network)
        .withNetworkAliases("gateway");
  }
}
