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
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.asserts.TopologyAssert;
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
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.lifecycle.Startables;

@Ignore
public class RollingUpdateTest {
  private static final String OLD_VERSION = VersionUtil.getPreviousVersion();
  private static final String NEW_VERSION = VersionUtil.getVersion();
  private static final String IMAGE_TAG = "current-test";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("task1", s -> s.zeebeJobType("firstTask"))
          .serviceTask("task2", s -> s.zeebeJobType("secondTask"))
          .endEvent()
          .done();

  private List<ZeebeBrokerContainer> containers;
  private String initialContactPoints;
  private Network network;

  @Before
  public void setup() {
    initialContactPoints =
        IntStream.range(0, 3)
            .mapToObj(id -> "broker-" + id + ":" + ZeebePort.INTERNAL_API.getPort())
            .collect(Collectors.joining(","));

    network = Network.newNetwork();

    containers =
        Arrays.asList(
            new ZeebeBrokerContainer(OLD_VERSION),
            new ZeebeBrokerContainer(OLD_VERSION),
            new ZeebeBrokerContainer(OLD_VERSION));

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
          .atMost(Duration.ofSeconds(5))
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
        .join(5, TimeUnit.SECONDS);
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

  private ZeebeClient newZeebeClient(final ZeebeBrokerContainer container) {
    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .brokerContactPoint(container.getExternalAddress(ZeebePort.GATEWAY))
        .build();
  }

  private ZeebeBrokerContainer upgradeBroker(final int index) {
    final var broker = new ZeebeBrokerContainer(IMAGE_TAG);
    containers.set(index, broker);
    return configureBrokerContainer(index, containers);
  }

  private ZeebeBrokerContainer configureBrokerContainer(
      final int index, final List<ZeebeBrokerContainer> brokers) {
    final int clusterSize = brokers.size();
    final var broker = brokers.get(index);
    final var hostName = "broker-" + index;
    broker.withNetworkAliases(hostName);

    // once old version is 0.24 and more membership configuration is exposed, further tune for fast
    // failure detection
    return broker
        .withNetwork(network)
        .withEnv("ZEEBE_BROKER_NETWORK_HOST", "0.0.0.0")
        .withEnv("ZEEBE_BROKER_NETWORK_ADVERTISED_HOST", hostName)
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERNAME", "zeebe-cluster")
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "128KB")
        .withEnv("ZEEBE_BROKER_CLUSTER_NODEID", String.valueOf(index))
        .withEnv("ZEEBE_BROKER_CLUSTER_CLUSTERSIZE", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_REPLICATIONFACTOR", String.valueOf(clusterSize))
        .withEnv("ZEEBE_BROKER_CLUSTER_INITIALCONTACTPOINTS", initialContactPoints)
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPFAILURETIMEOUT", "5000")
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPINTERVAL", "100")
        .withEnv("ZEEBE_BROKER_CLUSTER_GOSSIPPROBEINTERVAL", "100")
        .withLogLevel(Level.DEBUG)
        .withDebug(false);
  }
}
