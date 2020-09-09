/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.client.api.worker.JobHandler;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.ZeebePort;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.asserts.TopologyAssert;
import io.zeebe.util.VersionUtil;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.IoUtil;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

public class RollingUpdateTest {
  private static final String OLD_VERSION = VersionUtil.getPreviousVersion();
  private static final String NEW_VERSION = VersionUtil.getVersion();
  private static final String CURRENT_IMAGE_NAME = "camunda/zeebe:current-test";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task1", s -> s.zeebeJobType("firstTask"))
          .serviceTask("task2", s -> s.zeebeJobType("secondTask"))
          .endEvent()
          .done();

  private static final File SHARED_DATA;

  static {
    final var sharedDataPath =
        Optional.ofNullable(System.getenv("ZEEBE_CI_SHARED_DATA"))
            .map(Paths::get)
            .orElse(Paths.get(System.getProperty("tmpdir", "/tmp"), "shared"));
    SHARED_DATA = sharedDataPath.toAbsolutePath().toFile();
    IoUtil.ensureDirectoryExists(SHARED_DATA, "temporary folder for Docker");
  }

  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder(SHARED_DATA);

  private List<ZeebeContainer> containers;
  private String initialContactPoints;
  private Network network;

  @Before
  public void setup() {
    initialContactPoints =
        IntStream.range(0, 3)
            .mapToObj(id -> "broker-" + id + ":" + ZeebePort.INTERNAL.getPort())
            .collect(Collectors.joining(","));

    network = Network.newNetwork();
    containers =
        Arrays.asList(
            new ZeebeContainer("camunda/zeebe:" + OLD_VERSION),
            new ZeebeContainer("camunda/zeebe:" + OLD_VERSION),
            new ZeebeContainer("camunda/zeebe:" + OLD_VERSION));

    configureBrokerContainer(0, containers);
    configureBrokerContainer(1, containers);
    configureBrokerContainer(2, containers);
  }

  @After
  public void tearDown() {
    containers.parallelStream().forEach(Startable::stop);
  }

  @Test
  public void shouldBeAbleToRestartContainerWithNewVersion() {
    // given
    final var index = 0;
    Startables.deepStart(containers).join();
    containers.get(index).shutdownGracefully(Duration.ofSeconds(30));

    // when
    final var zeebeBrokerContainer = upgradeBroker(index);

    // then
    try (final var client = newZeebeClient(containers.get(1))) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, index));

      zeebeBrokerContainer.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpgradedBroker(client, index));
    }
  }

  @Test
  public void shouldReplicateSnapshotAcrossVersions() {
    // given
    Startables.deepStart(containers).join();

    // when
    final var availableBroker = containers.get(0);
    try (final var client = newZeebeClient(availableBroker)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      Awaitility.await("process instance creation")
          .atMost(Duration.ofSeconds(5))
          .pollInterval(Duration.ofMillis(100))
          .ignoreExceptions()
          .until(() -> createWorkflowInstance(client), Objects::nonNull)
          .getWorkflowInstanceKey();
    }

    try (final var client = newZeebeClient(availableBroker)) {
      final var brokerId = 1;
      var container = containers.get(brokerId);

      container.shutdownGracefully(Duration.ofSeconds(30));

      // until previous version points to 0.24, we cannot yet tune failure detection to be fast,
      // so wait long enough for the broker to be removed even in slower systems
      Awaitility.await("broker is removed from topology")
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

      // need to create enough records to allow for the creation of a snapshot
      for (int i = 0; i < 100; i++) {
        Awaitility.await("process instance creation")
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .ignoreExceptions()
            .until(() -> createWorkflowInstance(client), Objects::nonNull)
            .getWorkflowInstanceKey();
      }

      // wait for a snapshot - even if 0 is not the leader, it will get the replicated snapshot
      // which is a good indicator we now have a snapshot
      Awaitility.await("broker 0 has created a snapshot")
          .atMost(Duration.ofMinutes(2)) // twice the snapshot period
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(() -> assertBrokerHasAtLeastOneSnapshot(0));

      container = upgradeBroker(brokerId);
      container.start();
      Awaitility.await("upgraded broker is added to topology")
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpgradedBroker(client, brokerId));
    }

    // then
    assertBrokerHasAtLeastOneSnapshot(1);
  }

  @Test
  public void shouldPerformRollingUpgrade() {
    // given
    Startables.deepStart(containers).join();

    // when
    final long firstWorkflowInstanceKey;
    var availableBroker = containers.get(0);
    try (final var client = newZeebeClient(availableBroker)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      firstWorkflowInstanceKey =
          Awaitility.await("process instance creation")
              .atMost(Duration.ofSeconds(5))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createWorkflowInstance(client), Objects::nonNull)
              .getWorkflowInstanceKey();
    }

    for (int i = containers.size() - 1; i >= 0; i--) {
      try (final var client = newZeebeClient(availableBroker)) {
        final var brokerId = i;
        var container = containers.get(i);

        container.shutdownGracefully(Duration.ofSeconds(30));

        // until previous version points to 0.24, we cannot yet tune failure detection to be fast,
        // so wait long enough for the broker to be removed even in slower systems
        Awaitility.await("broker is removed from topology")
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

        container = upgradeBroker(i);
        container.start();
        Awaitility.await("upgraded broker is added to topology")
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyContainsUpgradedBroker(client, brokerId));

        availableBroker = container;
      }
    }

    // then
    final Map<Long, List<String>> activatedJobs = new HashMap<>();
    final var expectedOrderedJobs = List.of("firstTask", "secondTask");
    final JobHandler jobHandler =
        (jobClient, job) -> {
          jobClient.newCompleteCommand(job.getKey()).send().join();
          activatedJobs.compute(
              job.getWorkflowInstanceKey(),
              (ignored, list) -> {
                final var appendedList =
                    Optional.ofNullable(list).orElse(new CopyOnWriteArrayList<>());
                appendedList.add(job.getType());
                return appendedList;
              });
        };

    try (final var client = newZeebeClient(availableBroker)) {
      final var secondWorkflowInstanceKey = createWorkflowInstance(client).getWorkflowInstanceKey();
      final var expectedActivatedJobs =
          Map.of(
              firstWorkflowInstanceKey,
              expectedOrderedJobs,
              secondWorkflowInstanceKey,
              expectedOrderedJobs);
      client.newWorker().jobType("firstTask").handler(jobHandler).open();
      client.newWorker().jobType("secondTask").handler(jobHandler).open();

      Awaitility.await("all jobs have been activated")
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> assertThat(activatedJobs).isEqualTo(expectedActivatedJobs));
    }
  }

  private WorkflowInstanceEvent createWorkflowInstance(final ZeebeClient client) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .variables(Map.of("foo", "bar"))
        .send()
        .join();
  }

  private void deployProcess(final ZeebeClient client) {
    client
        .newDeployCommand()
        .addWorkflowModel(PROCESS, "process.bpmn")
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  private void assertTopologyContainsUpgradedBroker(
      final ZeebeClient zeebeClient, final int brokerId) {
    final var topology = zeebeClient.newTopologyRequest().send().join();
    TopologyAssert.assertThat(topology)
        .isComplete(containers.size(), 1)
        .hasBrokerSatisfying(
            brokerInfo -> {
              assertThat(brokerInfo.getNodeId()).isEqualTo(brokerId);
              assertThat(brokerInfo.getVersion()).isEqualTo(NEW_VERSION);
            });
  }

  private void assertTopologyDoesNotContainerBroker(final ZeebeClient client, final int brokerId) {
    final var topology = client.newTopologyRequest().send().join();
    TopologyAssert.assertThat(topology)
        .doesNotContainBroker(brokerId)
        .isComplete(containers.size() - 1, 1);
  }

  private ZeebeClient newZeebeClient(final ZeebeGatewayNode<?> gateway) {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .brokerContactPoint(gateway.getExternalGatewayAddress())
        .build();
  }

  private ZeebeContainer upgradeBroker(final int index) {
    final var broker = new ZeebeContainer(CURRENT_IMAGE_NAME);
    containers.set(index, broker);
    return configureBrokerContainer(index, containers);
  }

  private ZeebeContainer configureBrokerContainer(
      final int index, final List<ZeebeContainer> brokers) {
    final int clusterSize = brokers.size();
    final var broker = brokers.get(index);
    final var hostName = "broker-" + index;
    final var volumePath = getBrokerVolumePath(index);

    // once old version is 0.24 and more membership configuration is exposed, further tune for fast
    // failure detection
    return broker
        .withNetwork(network)
        .withNetworkAliases(hostName)
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", hostName)
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", initialContactPoints)
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPFAILURETIMEOUT", "5000")
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPINTERVAL", "100")
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPPROBEINTERVAL", "100")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
        .withFileSystemBind(volumePath.toString(), "/usr/local/zeebe/data");
  }

  private Path getBrokerVolumePath(final int index) {
    final var file = new File(tmpFolder.getRoot(), "broker-" + index);
    IoUtil.ensureDirectoryExists(file, "broker shared data folder");

    return file.toPath().toAbsolutePath();
  }

  private void assertBrokerHasAtLeastOneSnapshot(final int index) {
    final var path = getBrokerVolumePath(index);
    final var snapshotPath = path.resolve("raft-partition/partitions/1/snapshots");
    assertThat(snapshotPath).isNotEmptyDirectory();
  }
}
