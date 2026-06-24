/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static io.camunda.container.ClusterHelper.configureBroker;
import static io.camunda.container.ClusterHelper.createProcessInstance;
import static io.camunda.container.ClusterHelper.currentVersion;
import static io.camunda.container.ClusterHelper.deployProcess;
import static io.camunda.container.ClusterHelper.newClient;
import static io.camunda.container.ClusterHelper.updateBroker;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.cluster.BrokerNode;
import io.camunda.container.cluster.CamundaCluster;
import io.camunda.container.cluster.GatewayNode;
import io.camunda.container.volume.CamundaVolume;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.junit.CachedTestResultsExtension;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.ContainerState;

/**
 * These tests are here to detect when rolling update between one version to the next are not
 * possible. If we decide that they aren't between versions, we can disable or adjust assumptions.
 *
 * <p>The important part is that we should be aware whether rolling update is possible between
 * versions.
 */
final class RollingUpdateTest {

  private static List<Arguments> cachedVersionMatrix;

  @SuppressWarnings("unused")
  @RegisterExtension()
  private static final CachedTestResultsExtension CACHED_TEST_RESULTS_EXTENSION =
      new CachedTestResultsExtension(
          Optional.ofNullable(System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_REPORT"))
              .map(Path::of)
              .orElse(null));

  private final CamundaCluster cluster =
      CamundaCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withNodeConfig(
              node ->
                  node.withUnifiedConfig(
                          cfg -> {
                            cfg.getSystem().getUpgrade().setEnableVersionCheck(false);
                            cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                          })
                      .withEnv(UNPROTECTED_API_ENV_VAR, "true")
                      .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false"))
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  private final ContainerLogsDumper logsPrinter = new ContainerLogsDumper(cluster::getNodes);

  private final Collection<CamundaVolume> volumes = new LinkedList<>();

  static Stream<Arguments> versionMatrix() {
    if (cachedVersionMatrix == null) {
      cachedVersionMatrix = VersionCompatibilityMatrix.auto().toList();
    }
    return cachedVersionMatrix.stream();
  }

  @BeforeEach
  public void setup() {
    final var initialContactPoints =
        cluster.getBrokers().values().stream().map(BrokerNode::getInternalClusterAddress).toList();
    cluster
        .getBrokers()
        .values()
        .forEach(broker -> configureBroker(broker, initialContactPoints, volumes));
  }

  @AfterEach
  public void tearDown() {
    cluster.stop();
    CloseHelper.closeAll(volumes);
    volumes.clear();
  }

  @ParameterizedTest(name = "from {0} to {1}", allowZeroInvocations = true)
  @MethodSource("versionMatrix")
  void shouldBeAbleToRestartContainerWithNewVersion(final String from, final String to) {
    // given
    updateAllBrokers(from);

    final var index = 0;
    final BrokerNode<?> broker = cluster.getBrokers().get(index);
    cluster.start();

    // when
    broker.stop();
    updateBroker(broker, index, to);

    // then
    final GatewayNode<?> availableGateway = cluster.getAvailableGateway();
    try (final var client = newClient(availableGateway)) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, index));

      broker.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, index, to));
    }
  }

  @ParameterizedTest(name = "from {0} to {1}", allowZeroInvocations = true)
  @MethodSource("versionMatrix")
  void shouldReplicateSnapshotAcrossVersions(final String from, final String to) {
    // given
    updateAllBrokers(from);
    cluster.start();

    // when
    final GatewayNode<?> availableGateway = cluster.getAvailableGateway();
    try (final var client = newClient(availableGateway)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      createProcessInstance(client);
    }

    final var brokerId = 1;
    final BrokerNode<?> broker = cluster.getBrokers().get(brokerId);
    broker.stop();

    try (final var client = newClient(availableGateway)) {
      Awaitility.await("broker is removed from topology")
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

      for (int i = 0; i < 100; i++) {
        createProcessInstance(client);
      }

      // force the current leader to take a snapshot by just telling all the remaining nodes to take
      // a snapshot - one of which is bound to be the leader
      cluster.getBrokers().values().stream()
          .filter(ContainerState::isRunning)
          .forEach(this::takeSnapshot);

      // wait for a snapshot - even if 0 is not the leader, it will get the replicated snapshot
      // which is a good indicator we now have a snapshot
      Awaitility.await("broker 0 has created a snapshot")
          .atMost(Duration.ofMinutes(2)) // twice the snapshot period
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(() -> assertBrokerHasAtLeastOneSnapshot(0));

      updateBroker(broker, brokerId, to);
      broker.start();
      Awaitility.await("updated broker is added to topology")
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId, to));
    }

    Awaitility.await("until restarted broker has snapshot")
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertBrokerHasAtLeastOneSnapshot(brokerId));
  }

  @ParameterizedTest(name = "from {0} to {1}", allowZeroInvocations = true)
  @MethodSource("versionMatrix")
  void shouldPerformRollingUpdate(final String from, final String to) {
    // given
    updateAllBrokers(from);
    cluster.start();

    // when
    final long firstProcessInstanceKey;
    GatewayNode<?> availableGateway = cluster.getGateways().get("0");
    try (final var client = newClient(availableGateway)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      firstProcessInstanceKey = createProcessInstance(client);
    }

    for (int i = cluster.getBrokers().size() - 1; i >= 0; i--) {
      try (final CamundaClient client = newClient(availableGateway)) {
        final var brokerId = i;
        final BrokerNode<?> broker = cluster.getBrokers().get(i);
        broker.stop();

        Awaitility.await("broker is removed from topology")
            .atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

        updateBroker(broker, brokerId, to);
        broker.start();
        Awaitility.await("updated broker is added to topology")
            .atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId, to));

        availableGateway = cluster.getGateways().get(String.valueOf(i));
      }
    }

    // then
    final Map<Long, List<String>> activatedJobs = new HashMap<>();
    final var expectedOrderedJobs = List.of("firstTask", "secondTask");
    final JobHandler jobHandler =
        (jobClient, job) -> {
          jobClient.newCompleteCommand(job.getKey()).send().join();
          activatedJobs.compute(
              job.getProcessInstanceKey(),
              (ignored, list) -> {
                final var appendedList =
                    Optional.ofNullable(list).orElse(new CopyOnWriteArrayList<>());
                appendedList.add(job.getType());
                return appendedList;
              });
        };

    try (final var client = newClient(availableGateway)) {
      final var secondProcessInstanceKey = createProcessInstance(client);
      final var expectedActivatedJobs =
          Map.of(
              firstProcessInstanceKey,
              expectedOrderedJobs,
              secondProcessInstanceKey,
              expectedOrderedJobs);
      client.newWorker().jobType("firstTask").handler(jobHandler).open();
      client.newWorker().jobType("secondTask").handler(jobHandler).open();

      Awaitility.await("all jobs have been activated")
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThat(activatedJobs)
                      .as("the expected number of jobs has been activated")
                      .isEqualTo(expectedActivatedJobs));
    }
  }

  private void takeSnapshot(final BrokerNode<?> node) {
    PartitionsActuator.of(node).takeSnapshot();
  }

  private void updateAllBrokers(final String version) {
    cluster.getBrokers().forEach((id, broker) -> updateBroker(broker, id, version));
  }

  private void assertTopologyContainsUpdatedBroker(
      final CamundaClient client, final int brokerId, final String expectedVersion) {
    final var topology = client.newTopologyRequest().send().join();
    TopologyAssert.assertThat(topology)
        .as("the topology contains all the brokers")
        .isComplete(
            cluster.getBrokers().size(),
            cluster.getPartitionsCount(),
            cluster.getReplicationFactor())
        .as("the topology contains the updated broker")
        .hasBrokerSatisfying(
            brokerInfo -> {
              assertThat(brokerInfo.getNodeId()).as("the broker's node ID").isEqualTo(brokerId);
              assertThat(brokerInfo.getVersion())
                  .as("the broker's version")
                  .isEqualTo(
                      "CURRENT".equals(expectedVersion) ? currentVersion() : expectedVersion);
            });
  }

  private void assertTopologyDoesNotContainerBroker(
      final CamundaClient client, final int brokerId) {
    final var topology = client.newTopologyRequest().send().join();
    TopologyAssert.assertThat(topology)
        .as("the topology does not contain broker %d", brokerId)
        .doesNotContainBroker(brokerId)
        .hasLeaderForEachPartition(cluster.getPartitionsCount())
        .hasExpectedReplicasCount(cluster.getPartitionsCount(), cluster.getBrokers().size() - 1);
  }

  private void assertBrokerHasAtLeastOneSnapshot(final int index) {
    final BrokerNode<?> broker = cluster.getBrokers().get(index);
    final PartitionsActuator partitionsActuator = PartitionsActuator.of(broker);
    assertThat(partitionsActuator.query())
        .hasEntrySatisfying(
            1,
            status ->
                assertThat(status.snapshotId())
                    .as("partition 1 reports the presence of a snapshot")
                    .isNotBlank());
  }
}
