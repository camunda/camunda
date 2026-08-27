/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.createInstanceWithAJobOnAllPartitions;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.zeebe.it.cluster.backup.InProcessRestoreTestUtil;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** End-to-end coverage of the cluster rebalance interacting with recovery mode. */
@ZeebeIntegration
@Timeout(value = 10, unit = TimeUnit.MINUTES)
final class ClusterRebalanceRecoveryModeIT {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterRebalanceRecoveryModeIT.class);
  private static final String JOB_TYPE = "rebalance-recovery-test";
  private static final int PARTITION_COUNT = 3;

  @TestZeebe(purgeAfterEach = false)
  static TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITION_COUNT)
          .withReplicationFactor(3)
          .build();

  private static final ObjectMapper JSON = new ObjectMapper();
  @AutoClose private CamundaClient client;
  private ClusterRebalanceRestClient rebalanceClient;

  @BeforeEach
  void setUp() {
    // broker 1 is stopped/restarted to force an imbalance, so use another broker's client
    client = cluster.brokers().get(MemberId.from("0")).newClientBuilder().build();
    rebalanceClient = ClusterRebalanceRestClient.of(cluster.availableGateway());
  }

  @AfterEach
  void leaveRecovery() {
    ensureMode(PartitionStateCode.ACTIVE);
  }

  @Test
  void shouldAdmitAnEmptyNoOpRebalanceWhileTheClusterIsRecovering() {
    // given
    ensureMode(PartitionStateCode.RECOVERING);
    final var completedBefore = lastCompletedRebalance();

    // when
    final var triggered = rebalanceClient.triggerRebalance();
    final var dryRun = rebalanceClient.triggerDryRun();

    // then
    assertThat(statusOf(triggered))
        .as("recovering tenants are simply omitted from the plan, not a reason to refuse")
        .isEqualTo(HttpURLConnection.HTTP_ACCEPTED);
    assertThat(statusOf(dryRun)).isEqualTo(HttpURLConnection.HTTP_ACCEPTED);

    final var outcome = awaitTerminalRebalance(List.of("COMPLETED"));
    assertThat(outcome).isEqualTo("COMPLETED");
    final var completed = lastCompletedRebalance();
    assertThat(completed.path("partitions").isEmpty())
        .as("every tenant is recovering, so the plan is empty: %s", completed)
        .isTrue();
    assertThat(completedBefore.equals(completed))
        .as("a real no-op request still records its own completed run")
        .isFalse();
  }

  @Test
  void shouldAdmitARebalanceOnceTheClusterProcessesAgain() {
    // given
    ensureMode(PartitionStateCode.RECOVERING);
    ensureMode(PartitionStateCode.ACTIVE);
    forceBadLeaderDistribution();
    final var processInstanceKeys =
        createInstanceWithAJobOnAllPartitions(client, JOB_TYPE, PARTITION_COUNT);

    // when
    assertThat(statusOf(rebalanceClient.triggerRebalance()))
        .isEqualTo(HttpURLConnection.HTTP_ACCEPTED);

    // then
    awaitTerminalRebalance(List.of("COMPLETED"));
    awaitBalancedTopology();
    assertThatAllJobsCanBeCompleted(processInstanceKeys, client, JOB_TYPE);
  }

  @Test
  void shouldSkipRatherThanCancelWhenRecoveryRacesARunningRebalance() {
    // given
    forceBadLeaderDistribution();
    assertThat(hasBadLeaderDistribution()).isTrue();
    assertThat(statusOf(rebalanceClient.triggerRebalance()))
        .isEqualTo(HttpURLConnection.HTTP_ACCEPTED);

    // when
    ensureMode(PartitionStateCode.RECOVERING);

    // then
    final var outcome = awaitTerminalRebalance(List.of("COMPLETED"));
    LOG.info("Rebalance racing recovery finished as {}", outcome);
    final var completed = lastCompletedRebalance();
    final var results = completed.path("partitions").findValuesAsText("result");
    assertThat(results)
        .as("recovery skips partitions instead of cancelling the run: %s", completed)
        .allMatch(
            result ->
                result.equals("TRANSFERRED")
                    || result.equals("PHYSICAL_TENANT_RECOVERING")
                    || result.equals("ALREADY_LEADER"));

    ensureMode(PartitionStateCode.ACTIVE);
    final var processInstanceKeys =
        createInstanceWithAJobOnAllPartitions(client, JOB_TYPE, PARTITION_COUNT);
    assertThatAllJobsCanBeCompleted(processInstanceKeys, client, JOB_TYPE);
    assertThat(statusOf(rebalanceClient.triggerRebalance()))
        .as("a new rebalance is admitted once every tenant processes again")
        .isEqualTo(HttpURLConnection.HTTP_ACCEPTED);
    awaitTerminalRebalance(List.of("COMPLETED"));
  }

  private void ensureMode(final PartitionStateCode target) {
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    if (everyPartitionReports(actuator, target)) {
      return;
    }
    final var mode = target == PartitionStateCode.RECOVERING ? "RECOVERING" : "PROCESSING";
    final var changeId = InProcessRestoreTestUtil.changeMode(client, mode, false);
    Awaitility.await("the cluster transitions to " + mode)
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(actuator)
                    .hasCompletedChanges(changeId)
                    .doesNotHavePendingChanges());
    Awaitility.await("every partition reports " + target)
        .timeout(Duration.ofMinutes(1))
        .until(() -> everyPartitionReports(actuator, target));
  }

  private static boolean everyPartitionReports(
      final ClusterActuator actuator, final PartitionStateCode target) {
    return actuator.getTopology().getBrokers().stream()
        .map(BrokerState::getPartitions)
        .flatMap(List::stream)
        .map(PartitionState::getState)
        .allMatch(target::equals);
  }

  private String awaitTerminalRebalance(final List<String> legalResults) {
    final var terminalStatus = new AtomicReference<JsonNode>();
    Awaitility.await("the rebalance reaches a terminal state")
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () -> {
              final var status = rebalanceStatus();
              assertThat(status.path("runningRebalance").isNull())
                  .as("no rebalance still running: %s", status)
                  .isTrue();
              assertThat(status.path("lastCompletedRebalance").isNull())
                  .as("a completed rebalance is present: %s", status)
                  .isFalse();
              terminalStatus.set(status);
            });
    final var result = terminalStatus.get().path("lastCompletedRebalance").path("result").asText();
    assertThat(result).isIn(legalResults);
    return result;
  }

  private JsonNode rebalanceStatus() {
    final var response = rebalanceClient.getRebalance();
    final var body = readBody(response);
    assertThat(response.status())
        .as("rebalance status response: %s", body)
        .isEqualTo(HttpURLConnection.HTTP_OK);
    try {
      return JSON.readTree(body);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JsonNode lastCompletedRebalance() {
    return rebalanceStatus().path("lastCompletedRebalance");
  }

  private static int statusOf(final Response response) {
    final var body = readBody(response);
    LOG.debug("Rebalance request answered {}: {}", response.status(), body);
    return response.status();
  }

  private static String readBody(final Response response) {
    try (response) {
      final var body = response.body();
      if (body == null) {
        return "";
      }
      return new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void awaitBalancedTopology() {
    Awaitility.await("the final leader distribution is round-robin across brokers 0, 1, and 2")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                TopologyAssert.assertThat(client.newTopologyRequest().send().join())
                    .hasLeaderForPartition(1, 0)
                    .hasLeaderForPartition(2, 1)
                    .hasLeaderForPartition(3, 2));
  }

  @SuppressWarnings("resource")
  private void forceBadLeaderDistribution() {
    if (!hasGoodLeaderDistribution()) {
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
