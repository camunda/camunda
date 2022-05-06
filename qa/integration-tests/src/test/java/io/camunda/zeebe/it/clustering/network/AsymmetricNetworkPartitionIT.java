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
import com.github.dockerjava.api.model.HostConfig;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.agrona.LangUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class AsymmetricNetworkPartitionIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsymmetricNetworkPartitionIT.class);

  private final Network network = Network.newNetwork();

  @Container
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .withBrokerConfig(this::configureBroker)
          .withNetwork(network)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(cluster::getBrokers, LOGGER);

  private ZeebeClient client;

  static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.arguments(
            Named.named("Deployment distribution", new DeploymentDistributionTestCase())),
        Arguments.arguments(Named.named("Message correlation", new MessageCorrelationTestCase())));
  }

  @BeforeEach
  void beforeEach() {
    client = cluster.newClientBuilder().build();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(client, network);
  }

  @DisplayName("Withstand Asymmetric Network Partition")
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideTestCases")
  void shouldWithstandAsymmetricNetworkPartition(final AsymmetricNetworkPartitionTestCase testCase)
      throws IOException, InterruptedException {
    // given

    // the test only works if the leaders of partition 1 and 3 are different nodes
    Awaitility.await("partitions have a different leader")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(5))
        .until(this::hasEvenLeaderDistribution);

    final var topology = client.newTopologyRequest().send().join();
    final var firstLeader = getPartitionLeader(topology, 1);
    final var secondLeader = getPartitionLeader(topology, 2);
    final var firstLeaderIP =
        getContainerNetworkIP(cluster.getBrokers().get(firstLeader.getNodeId()));
    testCase.given(client);
    setupNetworkPartition(firstLeaderIP, cluster.getBrokers().get(secondLeader.getNodeId()));

    // when
    final var future = testCase.when(client);
    removeNetworkPartition(firstLeaderIP, cluster.getBrokers().get(secondLeader.getNodeId()));

    // then
    testCase.then(client, future);
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

  private void triggerRebalancing() {
    final var gateway = cluster.getGateways().values().stream().findFirst().orElseThrow();
    final var monitoringAddress = gateway.getExternalMonitoringAddress();
    final var httpClient = HttpClient.newHttpClient();
    final var request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(URI.create("http://" + monitoringAddress + "/actuator/rebalance"))
            .build();

    final HttpResponse<Void> response;
    try {
      response = httpClient.send(request, BodyHandlers.discarding());
      assertThat(response.statusCode()).isEqualTo(200);
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  private boolean hasEvenLeaderDistribution() {
    final var topology = client.newTopologyRequest().send().join();
    final var firstLeader = getPartitionLeader(topology, 1);
    final var otherLeader = getPartitionLeader(topology, 2);

    if (firstLeader.getNodeId() == otherLeader.getNodeId()) {
      LOGGER.info("Leader of all partitions is {}, re-balancing...", firstLeader.getNodeId());
      triggerRebalancing();
      return false;
    }

    return true;
  }

  /**
   * Removes the unreachable `ip route` added via {@link #setupNetworkPartition(String,
   * ZeebeBrokerNode)}
   */
  private void removeNetworkPartition(final String ip, final ZeebeBrokerNode<?> brokerNode)
      throws IOException, InterruptedException {
    final var execResult = runCommandInContainer(brokerNode, "ip route del unreachable " + ip);
    LOGGER.info("{}", execResult.getStdout());
  }

  /**
   * Set the given ip address unreachable for the given container.
   *
   * @param ipAddress the ip address which should be unreachable
   * @param brokerNode the broker container which should be updated
   * @throws IOException Can be thrown during running commands in the container
   * @throws InterruptedException Can be thrown during running commands in the container
   */
  private void setupNetworkPartition(
      final String ipAddress, final ZeebeBrokerNode<? extends GenericContainer<?>> brokerNode)
      throws IOException, InterruptedException {
    runCommandInContainer(brokerNode, "apt -qq update");
    runCommandInContainer(brokerNode, "apt install -qq -y iproute2");

    final var execResult =
        runCommandInContainer(brokerNode, "ip route add unreachable " + ipAddress);

    LOGGER.info("{}", execResult.getStdout());
  }

  private String getContainerNetworkIP(final ZeebeBrokerNode<?> node) {
    return node.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
        .findFirst()
        .orElseThrow()
        .getIpAddress();
  }

  private ExecResult runCommandInContainer(final ZeebeBrokerNode<?> container, final String command)
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

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    broker
        .self()
        .withCreateContainerCmdModifier(this::configureNetAdmin)
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "16MB");
  }

  private void configureNetAdmin(final CreateContainerCmd command) {
    final var hostConfig = Optional.ofNullable(command.getHostConfig()).orElse(new HostConfig());
    command.withHostConfig(hostConfig.withCapAdd(Capability.NET_ADMIN));
  }
}
