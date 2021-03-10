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
import io.zeebe.client.api.response.ProcessInstanceEvent;
import io.zeebe.client.api.worker.JobHandler;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.ZeebePort;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.PartitionsActuatorClient.PartitionStatus;
import io.zeebe.test.util.asserts.EitherAssert;
import io.zeebe.test.util.asserts.TopologyAssert;
import io.zeebe.test.util.testcontainers.ManagedVolume;
import io.zeebe.util.Either;
import io.zeebe.util.VersionUtil;
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
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;

@Ignore("https://github.com/zeebe-io/zeebe/issues/6007")
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

  private List<ZeebeContainer> containers;
  private List<ManagedVolume> volumes;
  private String initialContactPoints;
  private Network network;

  @Before
  public void setup() {
    initialContactPoints =
        IntStream.range(0, 3)
            .mapToObj(id -> "broker-" + id + ":" + ZeebePort.INTERNAL.getPort())
            .collect(Collectors.joining(","));

    network = Network.newNetwork();
    volumes =
        List.of(ManagedVolume.newVolume(), ManagedVolume.newVolume(), ManagedVolume.newVolume());
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
    if (containers != null) {
      containers.parallelStream().forEach(CloseHelper::quietClose);
    }

    CloseHelper.quietClose(network);
    CloseHelper.quietCloseAll(volumes);
  }

  @Test
  public void shouldBeAbleToRestartContainerWithNewVersion() {
    // given
    final var index = 0;
    Startables.deepStart(containers).join();
    containers.get(index).shutdownGracefully(Duration.ofSeconds(30));

    // when
    final var zeebeBrokerContainer = updateBroker(index);

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
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, index));
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
          .until(() -> createProcessInstance(client), Objects::nonNull)
          .getProcessInstanceKey();
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

      for (int i = 0; i < 100; i++) {
        Awaitility.await("process instance creation")
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .ignoreExceptions()
            .until(() -> createProcessInstance(client), Objects::nonNull)
            .getProcessInstanceKey();
      }

      // wait for a snapshot - even if 0 is not the leader, it will get the replicated snapshot
      // which is a good indicator we now have a snapshot
      Awaitility.await("broker 0 has created a snapshot")
          .atMost(Duration.ofMinutes(2)) // twice the snapshot period
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(() -> assertBrokerHasAtLeastOneSnapshot(0));

      container = updateBroker(brokerId);
      container.start();
      Awaitility.await("updated broker is added to topology")
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId));
    }

    assertBrokerHasAtLeastOneSnapshot(1);
  }

  @Test
  public void shouldPerformRollingUpdate() {
    // given
    Startables.deepStart(containers).join();

    // when
    final long firstProcessInstanceKey;
    var availableBroker = containers.get(0);
    try (final var client = newZeebeClient(availableBroker)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      firstProcessInstanceKey =
          Awaitility.await("process instance creation")
              .atMost(Duration.ofSeconds(5))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createProcessInstance(client), Objects::nonNull)
              .getProcessInstanceKey();
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

        container = updateBroker(i);
        container.start();
        Awaitility.await("updated broker is added to topology")
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId));

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
              job.getProcessInstanceKey(),
              (ignored, list) -> {
                final var appendedList =
                    Optional.ofNullable(list).orElse(new CopyOnWriteArrayList<>());
                appendedList.add(job.getType());
                return appendedList;
              });
        };

    try (final var client = newZeebeClient(availableBroker)) {
      final var secondProcessInstanceKey = createProcessInstance(client).getProcessInstanceKey();
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
          .untilAsserted(() -> assertThat(activatedJobs).isEqualTo(expectedActivatedJobs));
    }
  }

  private ProcessInstanceEvent createProcessInstance(final ZeebeClient client) {
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
        .addProcessModel(PROCESS, "process.bpmn")
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  private void assertTopologyContainsUpdatedBroker(
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
        .gatewayAddress(gateway.getExternalGatewayAddress())
        .build();
  }

  private ZeebeContainer updateBroker(final int index) {
    final var broker =
        new ZeebeContainer(CURRENT_IMAGE_NAME)
            .withVolumesFrom(containers.get(index), BindMode.READ_WRITE);
    containers.set(index, broker);
    return configureBrokerContainer(index, containers);
  }

  private ZeebeContainer configureBrokerContainer(
      final int index, final List<ZeebeContainer> brokers) {
    final int clusterSize = brokers.size();
    final var broker = brokers.get(index);
    final var hostName = "broker-" + index;
    final var volume = volumes.get(index);

    return broker
        .withNetwork(network)
        .withNetworkAliases(hostName)
        .withCreateContainerCmdModifier(volume::attachVolumeToContainer)
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISEDHOST", hostName)
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", initialContactPoints)
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_BROADCASTUPDATES", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SYNCINTERVAL", "250ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBEINTERVAL", "250ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBETIMEOUT", "1s")
        .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG");
  }

  private void assertBrokerHasAtLeastOneSnapshot(final int index) {
    final ZeebeContainer broker = containers.get(index);
    final PartitionsActuatorClient partitionsActuatorClient =
        new PartitionsActuatorClient(broker.getExternalMonitoringAddress());

    final Either<Throwable, Map<String, PartitionStatus>> response =
        partitionsActuatorClient.queryPartitions();
    EitherAssert.assertThat(response).isRight();

    final PartitionStatus partitionStatus = response.get().get("1");
    assertThat(partitionStatus).isNotNull();
    assertThat(partitionStatus.snapshotId).isNotBlank();
  }
}
