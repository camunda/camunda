/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Black-box coverage for rebalances started on a schedule, gated on the cluster being quiet. */
@ZeebeIntegration
final class ScheduledRebalanceIT {
  private static final String PROCESS_ID = "scheduled-rebalance";
  private static final MemberId COORDINATOR = MemberId.from("0");
  private static final double MAX_PROCESS_INSTANCES_PER_SECOND = 1;

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> {
                        final var rebalance = cfg.getCluster().getRaft().getRebalance();
                        rebalance.setSchedule("PT10S");
                        rebalance.setLoadWindow(Duration.ofSeconds(20));
                        rebalance.setMaxProcessInstancesPerSecond(MAX_PROCESS_INSTANCES_PER_SECOND);
                      }))
          .build();

  @AutoClose private CamundaClient client;
  private final ScheduledExecutorService load = Executors.newSingleThreadScheduledExecutor();

  @BeforeEach
  void setup() {
    client = cluster.brokers().get(COORDINATOR).newClientBuilder().build();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done(),
            PROCESS_ID + ".bpmn")
        .send()
        .join();
  }

  @AfterEach
  void stopLoad() {
    load.shutdownNow();
  }

  @Test
  void shouldOnlyRebalanceOnceTheClusterIsQuiet() {
    // given
    load.scheduleAtFixedRate(this::createInstance, 0, 100, TimeUnit.MILLISECONDS);
    forceBadLeaderDistribution();

    // when
    Awaitility.await("a scheduled rebalance is skipped as the cluster is busy")
        .atMost(Duration.ofMinutes(2))
        .until(() -> scheduledRuns("BUSY") > 0);

    // then
    assertThat(measuredLoad("ROOT_PROCESS_INSTANCES"))
        .isGreaterThan(MAX_PROCESS_INSTANCES_PER_SECOND);
    assertThat(measuredLoad("PROCESSED_COMMANDS")).isPositive();
    assertThat(scheduledRuns("STARTED")).isZero();
    assertThat(hasBadLeaderDistribution()).isTrue();

    // when
    load.shutdownNow();

    // then
    Awaitility.await("a scheduled rebalance starts once the cluster is quiet")
        .atMost(Duration.ofMinutes(2))
        .until(() -> scheduledRuns("STARTED") > 0);
    Awaitility.await("leadership is balanced")
        .atMost(Duration.ofMinutes(1))
        .until(this::hasGoodLeaderDistribution);
  }

  private void createInstance() {
    client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send();
  }

  private double scheduledRuns(final String result) {
    final var counter =
        coordinatorMetrics()
            .find("zeebe.cluster.rebalance.scheduled.total")
            .tag("result", result)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private double measuredLoad(final String measure) {
    return coordinatorMetrics()
        .get("zeebe.cluster.rebalance.scheduled.load")
        .tag("measure", measure)
        .gauge()
        .value();
  }

  private MeterRegistry coordinatorMetrics() {
    return cluster.brokers().get(COORDINATOR).bean(MeterRegistry.class);
  }

  @SuppressWarnings("resource")
  private void forceBadLeaderDistribution() {
    if (hasBadLeaderDistribution()) {
      return;
    }
    final var stoppedBroker = cluster.brokers().get(MemberId.from("1")).stop();
    Awaitility.await("at least one broker is leader for more than one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasBadLeaderDistribution);
    stoppedBroker.start().await(TestHealthProbe.READY);
    stoppedBroker.awaitCompleteTopology(
        cluster.brokers().size(),
        cluster.partitionsCount(),
        cluster.replicationFactor(),
        Duration.ofMinutes(1));
  }

  private boolean hasBadLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .anyMatch(
            broker -> broker.getPartitions().stream().filter(PartitionInfo::isLeader).count() > 1);
  }

  private boolean hasGoodLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .allMatch(
            broker -> broker.getPartitions().stream().filter(PartitionInfo::isLeader).count() == 1);
  }
}
