/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.ZeebeVolume;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * These tests are here to detect when rolling update between one version to the next are not
 * possible. If we decide that they aren't between versions, we can disable or adjust assumptions.
 *
 * <p>The important part is that we should be aware whether rolling update is possible between
 * versions.
 */
final class RollingUpdateTest {

  private static final DockerImageName PREVIOUS_VERSION =
      DockerImageName.parse("camunda/zeebe").withTag(VersionUtil.getPreviousVersion());
  private static final DockerImageName CURRENT_VERSION =
      ZeebeTestContainerDefaults.defaultTestImage();
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task1", s -> s.zeebeJobType("firstTask"))
          .serviceTask("task2", s -> s.zeebeJobType("secondTask"))
          .endEvent()
          .done();
  private final Network network = Network.newNetwork();
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withNetwork(network)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  private final ContainerLogsDumper logsPrinter = new ContainerLogsDumper(cluster::getNodes);

  @BeforeEach
  public void setup() {
    cluster.getBrokers().values().forEach(this::configureBroker);
  }

  @AfterEach
  public void tearDown() {
    cluster.stop();
    CloseHelper.quietClose(network);
  }

  @Test
  void shouldBeAbleToRestartContainerWithNewVersion() {
    // given
    final var index = 0;
    final ZeebeBrokerNode<?> broker = cluster.getBrokers().get(index);
    cluster.start();

    // when
    broker.stop();
    updateBroker(broker);

    // then
    try (final var client = cluster.newClientBuilder().build()) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, index));

      broker.start();

      Awaitility.await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, index));
    }
  }

  @Test
  void shouldReplicateSnapshotAcrossVersions() {
    // given
    cluster.start();

    // when
    try (final var client = cluster.newClientBuilder().build()) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      Awaitility.await("process instance creation")
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(100))
          .ignoreExceptions()
          .until(() -> createProcessInstance(client), Objects::nonNull)
          .getProcessInstanceKey();
    }

    final var brokerId = 1;
    final ZeebeBrokerNode<?> broker = cluster.getBrokers().get(brokerId);
    broker.stop();

    try (final var client = cluster.newClientBuilder().build()) {
      Awaitility.await("broker is removed from topology")
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

      for (int i = 0; i < 100; i++) {
        Awaitility.await("process instance creation")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .ignoreExceptions()
            .until(() -> createProcessInstance(client), Objects::nonNull)
            .getProcessInstanceKey();
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

      updateBroker(broker);
      broker.start();
      Awaitility.await("updated broker is added to topology")
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId));
    }

    assertBrokerHasAtLeastOneSnapshot(1);
  }

  @Test
  void shouldPerformRollingUpdate() {
    // given
    cluster.start();

    // when
    final long firstProcessInstanceKey;
    ZeebeGatewayNode<?> availableGateway = cluster.getGateways().get("0");
    try (final var client = newZeebeClient(availableGateway)) {
      deployProcess(client);

      // potentially retry in case we're faster than the deployment distribution
      firstProcessInstanceKey =
          Awaitility.await("process instance creation")
              .atMost(Duration.ofSeconds(30))
              .pollInterval(Duration.ofMillis(100))
              .ignoreExceptions()
              .until(() -> createProcessInstance(client), Objects::nonNull)
              .getProcessInstanceKey();
    }

    for (int i = cluster.getBrokers().size() - 1; i >= 0; i--) {
      try (final ZeebeClient client = newZeebeClient(availableGateway)) {
        final var brokerId = i;
        final ZeebeBrokerNode<?> broker = cluster.getBrokers().get(i);
        broker.stop();

        Awaitility.await("broker is removed from topology")
            .atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyDoesNotContainerBroker(client, brokerId));

        updateBroker(broker);
        broker.start();
        Awaitility.await("updated broker is added to topology")
            .atMost(Duration.ofSeconds(120))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> assertTopologyContainsUpdatedBroker(client, brokerId));

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

    try (final var client = newZeebeClient(availableGateway)) {
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
          .untilAsserted(
              () ->
                  assertThat(activatedJobs)
                      .as("the expected number of jobs has been activated")
                      .isEqualTo(expectedActivatedJobs));
    }
  }

  private void takeSnapshot(final ZeebeBrokerNode<?> node) {
    PartitionsActuator.of(node).takeSnapshot();
  }

  private void updateBroker(final ZeebeBrokerNode<?> broker) {
    broker.setDockerImageName(CURRENT_VERSION.asCanonicalNameString());
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
        .newDeployResourceCommand()
        .addProcessModel(PROCESS, "process.bpmn")
        .send()
        .join(10, TimeUnit.SECONDS);
  }

  private void assertTopologyContainsUpdatedBroker(
      final ZeebeClient zeebeClient, final int brokerId) {
    final var topology = zeebeClient.newTopologyRequest().send().join();
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
                  .isEqualTo(VersionUtil.getVersion());
            });
  }

  private void assertTopologyDoesNotContainerBroker(final ZeebeClient client, final int brokerId) {
    final var topology = client.newTopologyRequest().send().join();
    TopologyAssert.assertThat(topology)
        .as("the topology does not contain broker %d", brokerId)
        .doesNotContainBroker(brokerId)
        .hasLeaderForEachPartition(cluster.getPartitionsCount())
        .hasExpectedReplicasCount(cluster.getPartitionsCount(), cluster.getBrokers().size() - 1);
  }

  private ZeebeClient newZeebeClient(final ZeebeGatewayNode<?> gateway) {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .gatewayAddress(gateway.getExternalGatewayAddress())
        .build();
  }

  private void assertBrokerHasAtLeastOneSnapshot(final int index) {
    final ZeebeBrokerNode<?> broker = cluster.getBrokers().get(index);
    final PartitionsActuator partitionsActuator = PartitionsActuator.of(broker);
    assertThat(partitionsActuator.query())
        .hasEntrySatisfying(
            1,
            status ->
                assertThat(status.snapshotId())
                    .as("partition 1 reports the presence of a snapshot")
                    .isNotBlank());
  }

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    broker
        .withZeebeData(ZeebeVolume.newVolume())
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_BROADCASTUPDATES", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SYNCINTERVAL", "250ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBEINTERVAL", "100ms")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_PROBETIMEOUT", "1s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_FAILURETIMEOUT", "2s")
        .withEnv("ZEEBE_BROKER_CLUSTER_MEMBERSHIP_SUSPECTPROBES", "2")
        // ensure we have an exporter present to test sharing exporter state across nodes
        .withEnv("ZEEBE_BROKER_EXECUTIONMETRICSEXPORTERENABLED", "true")
        .withEnv("ZEEBE_LOG_LEVEL", "DEBUG");
    broker.setDockerImageName(PREVIOUS_VERSION.asCanonicalNameString());
  }
}
