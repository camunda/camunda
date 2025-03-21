/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.network;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.zeebe.it.util.ZeebeContainerUtil.newClientBuilder;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Network.Ipam;
import com.github.dockerjava.api.model.Network.Ipam.Config;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;

@EnabledOnOs(
    value = OS.LINUX,
    disabledReason =
        "The Docker documentation says that IPv6 networking is only supported on Docker daemons running on Linux hosts. See: https://docs.docker.com/config/daemon/ipv6/")
final class Ipv6IntegrationTest {
  private static final String BASE_PART_OF_SUBNET = "2081::aede:4844:fe00:";
  private static final String SUBNET = BASE_PART_OF_SUBNET + "0/123";
  private static final String GATEWAY_IP = String.format("%s%d", BASE_PART_OF_SUBNET, 2);
  private static final String BROKER_IP = String.format("%s%d", BASE_PART_OF_SUBNET, 3);
  private static final String INADDR6_ANY = "[::]";
  private final Network network =
      Network.builder()
          .createNetworkCmdModifier(
              createNetworkCmd ->
                  createNetworkCmd
                      .withIpam(new Ipam().withConfig(new Config().withSubnet(SUBNET)))
                      .withName(UUID.randomUUID().toString()))
          .enableIpv6(true)
          .build();
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withNetwork(network)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withBrokerConfig(this::configureBroker)
          .withBrokersCount(1)
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withGatewayConfig(this::configureGateway)
          .build();

  @SuppressWarnings({"resource", "Convert2MethodRef", "ResultOfMethodCallIgnored"})
  @BeforeEach
  void beforeEach() {
    final var networkInfo =
        DockerClientFactory.lazyClient().inspectNetworkCmd().withNetworkId(network.getId()).exec();

    Assertions.assertThat(networkInfo)
        .as("IPv6 network was properly created")
        .isNotNull()
        .extracting(n -> n.getEnableIPv6());
  }

  @AfterEach
  void tearDown() {
    CloseHelper.closeAll(cluster, network);
  }

  @Test
  void shouldCommunicateOverIpv6() {
    // given
    cluster.start();

    // when
    try (final var client = newClientBuilder(cluster).build(); ) {
      final Topology topology = client.newTopologyRequest().send().join(5, TimeUnit.SECONDS);
      // then - can find each other
      TopologyAssert.assertThat(topology).isComplete(1, 1, 1);
    }
  }

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    final var hostName = String.format("[%s]", BROKER_IP);

    broker
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        .withEnv("ATOMIX_LOG_LEVEL", "INFO")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", hostName)
        .withEnv("ZEEBE_BROKER_NETWORK_HOST", INADDR6_ANY)
        .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
        .withCreateContainerCmdModifier(cmd -> configureHostForIPv6(cmd, BROKER_IP));
  }

  private void configureHostForIPv6(
      final CreateContainerCmd cmd, final String hostNameWithoutBraces) {
    final var hostConfig = Optional.ofNullable(cmd.getHostConfig()).orElse(new HostConfig());
    cmd.withHostConfig(hostConfig.withNetworkMode(network.getId()));
    cmd.withIpv6Address(hostNameWithoutBraces).withHostName(hostNameWithoutBraces);
  }

  private void configureGateway(final ZeebeGatewayNode<?> gateway) {
    final var hostName = String.format("[%s]", GATEWAY_IP);

    gateway
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        .withEnv("ATOMIX_LOG_LEVEL", "INFO")
        .withEnv(
            "ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT",
            String.format("[%s]:%d", BROKER_IP, ZeebePort.INTERNAL.getPort()))
        .withEnv("ZEEBE_GATEWAY_NETWORK_HOST", INADDR6_ANY)
        .withEnv("ZEEBE_GATEWAY_NETWORK_ADVERTISEDHOST", hostName)
        .withEnv("ZEEBE_GATEWAY_CLUSTER_HOST", hostName)
        .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
        .withCreateContainerCmdModifier(cmd -> configureHostForIPv6(cmd, GATEWAY_IP));
  }
}
