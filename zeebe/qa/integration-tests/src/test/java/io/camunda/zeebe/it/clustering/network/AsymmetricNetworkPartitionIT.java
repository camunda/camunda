/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.network;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;
import static io.camunda.zeebe.it.util.ZeebeContainerUtil.newClientBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeNode;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class AsymmetricNetworkPartitionIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(AsymmetricNetworkPartitionIT.class);

  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final ZeebeCluster CLUSTER =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(2)
          .withReplicationFactor(3)
          .withBrokerConfig(AsymmetricNetworkPartitionIT::configureBroker)
          .withNetwork(NETWORK)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(CLUSTER::getBrokers, LOGGER);

  private CamundaClient client;

  static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.arguments(
            Named.named("Deployment distribution", new DeploymentDistributionTestCase())),
        Arguments.arguments(Named.named("Message correlation", new MessageCorrelationTestCase())));
  }

  @BeforeAll
  static void beforeAll() {
    CLUSTER.getBrokers().forEach((id, broker) -> installNetworkUtilities(broker));
  }

  @AfterAll
  static void afterAll() {
    CloseHelper.quietClose(NETWORK);
  }

  @BeforeEach
  void beforeEach() {
    client = newClientBuilder(CLUSTER).build();
    CLUSTER.getBrokers().forEach((id, broker) -> clearUnreachableRoutes(broker));
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(client);
  }

  @DisplayName("Withstand Asymmetric Network Partition")
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideTestCases")
  void shouldWithstandAsymmetricNetworkPartition(
      final AsymmetricNetworkPartitionTestCase testCase) {
    // given

    // the test only works if the leaders of partition 1 and 2 are different nodes
    Awaitility.await("partitions have a different leader")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(5))
        .until(this::hasEvenLeaderDistribution);

    final var topology = client.newTopologyRequest().send().join();
    final var firstLeader = getPartitionLeader(topology, 1).orElseThrow();
    final var secondLeader = getPartitionLeader(topology, 2).orElseThrow();

    final var firstLeaderIP =
        getContainerNetworkIP(CLUSTER.getBrokers().get(firstLeader.getNodeId()));
    testCase.given(client);
    setupNetworkPartition(firstLeaderIP, CLUSTER.getBrokers().get(secondLeader.getNodeId()));

    // when
    final var future = testCase.when(client);
    clearUnreachableRoutes(CLUSTER.getBrokers().get(secondLeader.getNodeId()));

    // then
    testCase.then(client, future);
  }

  private Optional<BrokerInfo> getPartitionLeader(final Topology topology, final int partition) {
    return topology.getBrokers().stream()
        .filter(
            b ->
                b.getPartitions().stream()
                    .filter(p -> p.getPartitionId() == partition)
                    .anyMatch(PartitionInfo::isLeader))
        .findFirst();
  }

  private void triggerRebalancing() {
    final var gateway = CLUSTER.getGateways().values().stream().findFirst().orElseThrow();
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

    if (firstLeader.isEmpty() || otherLeader.isEmpty()) {
      LOGGER.debug("Not all partitions have a leader yet...");
      return false;
    }

    if (firstLeader.get().getNodeId() == otherLeader.get().getNodeId()) {
      LOGGER.info("Leader of all partitions is {}, re-balancing...", firstLeader.get().getNodeId());
      triggerRebalancing();
      return false;
    }

    return true;
  }

  /**
   * Set the given ip address unreachable for the given container.
   *
   * @param ipAddress the ip address which should be unreachable
   * @param brokerNode the broker container which should be updated
   */
  private void setupNetworkPartition(final String ipAddress, final ZeebeNode<?> brokerNode) {
    exec(brokerNode, "ip route add unreachable " + ipAddress);
  }

  private String getContainerNetworkIP(final ZeebeBrokerNode<?> node) {
    return node.getContainerInfo().getNetworkSettings().getNetworks().values().stream()
        .findFirst()
        .orElseThrow()
        .getIpAddress();
  }

  /**
   * Clears all unreachable routes from the given container, i.e. those added via {@link
   * #setupNetworkPartition(String, ZeebeNode)}.
   */
  private static void clearUnreachableRoutes(final ZeebeNode<?> container) {
    exec(container, "ip route flush type unreachable");
  }

  /**
   * Installs required network utilities to block certain hosts. Additionally, backs up the routes
   * table, so we can restore it between tests.
   */
  private static void installNetworkUtilities(final ZeebeNode<?> container) {
    exec(container, "apt-get -qq update");
    exec(container, "apt-get -qq -y install iproute2");
  }

  private static void exec(final ZeebeNode<?> container, final String command) {
    LOGGER.info("Executing command {} on container {}", command, container.getContainerId());

    // can't use container.execInContainer here as we need to switch to the root user
    try {
      final String commandId =
          container
              .getDockerClient()
              .execCreateCmd(container.getContainerId())
              .withAttachStdout(true)
              .withUser("root")
              .withCmd(command.split(" "))
              .exec()
              .getId();

      final ToStringConsumer stdoutConsumer = new ToStringConsumer();
      try (final FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
        callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer);
        container.getDockerClient().execStartCmd(commandId).exec(callback).awaitCompletion();
      }

      final Long exitCode =
          container.getDockerClient().inspectExecCmd(commandId).exec().getExitCodeLong();
      assertThat(exitCode)
          .as(
              "command [%s] failed with status [%d] and output: [%s]",
              command, exitCode, stdoutConsumer.toUtf8String())
          .isEqualTo(0);

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      LangUtil.rethrowUnchecked(e);
    }

    LOGGER.info("Executed command {} on container {}", command, container.getContainerId());
  }

  private static void configureBroker(final ZeebeBrokerNode<?> broker) {
    broker
        .self()
        .withCreateContainerCmdModifier(AsymmetricNetworkPartitionIT::configureNetAdmin)
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "16MB")
        .withEnv(CREATE_SCHEMA_ENV_VAR, "false");
  }

  private static void configureNetAdmin(final CreateContainerCmd command) {
    final var hostConfig = Optional.ofNullable(command.getHostConfig()).orElse(new HostConfig());
    command.withHostConfig(hostConfig.withCapAdd(Capability.NET_ADMIN));
  }
}
