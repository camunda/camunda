/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

public class AsymmetricNetworkPartitionIT {

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
        Arguments.of(
            "Deployment distribution",
            (Consumer<ZeebeClient>)
                (client) -> { // given
                  final var process =
                      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
                  client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join();
                },
            (Consumer<ZeebeClient>)
                (client) -> { // then
                  final var topology = client.newTopologyRequest().send().join();

                  final var partitions =
                      IntStream.range(1, topology.getPartitionsCount() + 1)
                          .boxed()
                          .collect(Collectors.toSet());

                  Awaitility.await("should be able to create instances on all partitions")
                      .ignoreExceptions()
                      .atMost(Duration.ofMinutes(1))
                      .until(
                          () -> {
                            final var processInstanceEvent =
                                client
                                    .newCreateInstanceCommand()
                                    .bpmnProcessId("process")
                                    .latestVersion()
                                    .send()
                                    .join();

                            return Protocol.decodePartitionId(
                                processInstanceEvent.getProcessInstanceKey());
                          },
                          (partitionId) -> {
                            LOGGER.info("Instance created on partition: {}", partitionId);
                            partitions.remove(partitionId);
                            return partitions.isEmpty();
                          });
                }));
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(cluster, zeebeClient);
  }

  @ParameterizedTest
  @MethodSource("provideTestCases")
  public void shouldWithstandAsymmetricNetworkPartition(
      final String name, final Consumer<ZeebeClient> given, final Consumer<ZeebeClient> then)
      throws IOException, InterruptedException {
    // given
    LOGGER.info("Run test {}", name);
    setupZeebeCluster();
    zeebeClient = cluster.newClientBuilder().build();

    final var topology = zeebeClient.newTopologyRequest().send().join();
    final var leaderOfPartitionOne = getPartitionLeader(topology, 1);
    final var leaderOfPartitionThree = getPartitionLeader(topology, 3);
    assertThat(leaderOfPartitionOne.getNodeId()).isNotEqualTo(leaderOfPartitionThree.getNodeId());

    final var ipAddress =
        getContainerIpAddress(getContainerForNodeId(leaderOfPartitionOne.getNodeId()));
    setupAsymmetricNetworkPartition(
        ipAddress, getContainerForNodeId(leaderOfPartitionThree.getNodeId()));

    given.accept(zeebeClient);

    // when
    removeAsymmetricNetworkPartition(
        ipAddress, getContainerForNodeId(leaderOfPartitionThree.getNodeId()));

    // then
    then.accept(zeebeClient);
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
      LOGGER.error("Command {} failed with code: {}", command, execResult.getExitCode());
      LOGGER.error("Stderr: {}", execResult.getStderr());
    }

    return execResult;
  }

  /** Set ups Zeebe Cluster with necessary capabilities in order to create network partitions. */
  private void setupZeebeCluster() {
    cluster =
        ZeebeCluster.builder()
            .withImage(DockerImageName.parse("camunda/zeebe:SNAPSHOT"))
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
