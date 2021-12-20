/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

final class AsymmetricNetworkPartitionIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsymmetricNetworkPartitionIT.class);
  private ZeebeCluster cluster;

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> cluster.getBrokers(), LOGGER);

  private ZeebeClient zeebeClient;

  @SuppressWarnings("unused")
  public static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.arguments(
            Named.named("Deployment distribution", new DeploymentDistributionTestCase())),
        Arguments.arguments(Named.named("Message correlation", new MessageCorrelationTestCase())));
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(cluster, zeebeClient);
  }

  @DisplayName("Withstand Asymmetric Network Partition")
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideTestCases")
  public void shouldWithstandAsymmetricNetworkPartition(
      final AsymmetricNetworkPartitionTestCase asymmetricNetworkPartitionTestCase)
      throws IOException, InterruptedException {
    // given
    setupZeebeCluster();
    zeebeClient = cluster.newClientBuilder().build();

    final var topology = zeebeClient.newTopologyRequest().send().join();
    final var leaderOfPartitionOne = getPartitionLeader(topology, 1);
    final var leaderOfPartitionThree = getPartitionLeader(topology, 3);
    assertThat(leaderOfPartitionOne.getNodeId()).isNotEqualTo(leaderOfPartitionThree.getNodeId());

    final var ipAddress =
        getContainerIpAddress(getContainerForNodeId(leaderOfPartitionOne.getNodeId()));
    asymmetricNetworkPartitionTestCase.given(zeebeClient);
    setupAsymmetricNetworkPartition(
        ipAddress, getContainerForNodeId(leaderOfPartitionThree.getNodeId()));

    // when
    final var future = asymmetricNetworkPartitionTestCase.when(zeebeClient);
    removeAsymmetricNetworkPartition(
        ipAddress, getContainerForNodeId(leaderOfPartitionThree.getNodeId()));

    // then
    asymmetricNetworkPartitionTestCase.then(zeebeClient, future);
  }

  private BrokerInfo getPartitionLeader(final Topology topology, final int partition) {
    return topology.getBrokers().stream()
        .filter(
            b ->
                b.getPartitions().stream()
                    .filter(p -> p.getPartitionId() == partition)
                    .anyMatch(PartitionInfo::isLeader))
        .findFirst()
        .orElseThrow();
  }

  private void removeAsymmetricNetworkPartition(
      final String ipAddress, final ZeebeBrokerNode<? extends GenericContainer<?>> brokerNode)
      throws IOException, InterruptedException {
    LOGGER.info(
        "{}",
        runCommandInContainer(brokerNode, "ip route del unreachable " + ipAddress).getStdout());
  }

  /**
   * Set the given ip address unreachable for the given container.
   *
   * @param ipAddress the ip address which should be unreachable
   * @param brokerNode the broker container which should be updated
   * @throws IOException Can be thrown during running commands in the container
   * @throws InterruptedException Can be thrown during running commands in the container
   */
  private void setupAsymmetricNetworkPartition(
      final String ipAddress, final ZeebeBrokerNode<? extends GenericContainer<?>> brokerNode)
      throws IOException, InterruptedException {
    runCommandInContainer(brokerNode, "apt update");
    runCommandInContainer(brokerNode, "apt install -y iproute2");
    LOGGER.info(
        "{}",
        runCommandInContainer(brokerNode, "ip route add unreachable " + ipAddress).getStdout());
  }

  private ZeebeBrokerNode<? extends GenericContainer<?>> getContainerForNodeId(final int nodeId) {
    return cluster.getBrokers().get(nodeId);
  }

  private String getContainerIpAddress(
      final ZeebeBrokerNode<? extends GenericContainer<?>> leaderOneNode) {
    return leaderOneNode
        .getCurrentContainerInfo()
        .getNetworkSettings()
        .getNetworks()
        .values()
        .stream()
        .findFirst()
        .orElseThrow()
        .getIpAddress();
  }

  private ExecResult runCommandInContainer(
      final ZeebeBrokerNode<? extends GenericContainer<?>> container, final String command)
      throws IOException, InterruptedException {
    LOGGER.info("Run command: {}", command);

    final var commands = command.split(" ");
    final var execResult = container.execInContainer(commands);

    if (execResult.getExitCode() == 0) {
      LOGGER.info("Command {} was successful.", command);
    } else {
      final var errorMessage =
          String.format(
              "Command '%s' failed with code: %d stderr: '%s'",
              command, execResult.getExitCode(), execResult.getStderr());
      fail(errorMessage);
    }

    return execResult;
  }

  /** Set ups Zeebe Cluster with necessary capabilities in order to create network partitions. */
  private void setupZeebeCluster() {
    cluster =
        ZeebeCluster.builder()
            .withImage(ZeebeTestContainerDefaults.defaultTestImage())
            .withBrokersCount(3)
            .withEmbeddedGateway(true)
            .withPartitionsCount(3)
            .withReplicationFactor(3)
            .build();
    cluster
        .getBrokers()
        .forEach(
            (nodeId, broker) -> {
              ((ZeebeContainer) broker)
                  .withCreateContainerCmdModifier(
                      (CreateContainerCmd it) ->
                          it.withHostConfig(it.getHostConfig().withCapAdd(Capability.NET_ADMIN)));

              // smaller sizes to make the tests faster
              broker
                  .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
                  .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "16MB");
            });
    cluster.start();
  }
}
