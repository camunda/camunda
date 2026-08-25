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
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.StreamSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Black-box coverage for {@code POST /cluster/v2/rebalance}. */
@ZeebeIntegration
final class ClusterRebalanceIT {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterRebalanceIT.class);
  private static final String JOB_TYPE = "rebalance-test";
  private static final int PARTITION_COUNT = 3;
  private static final ObjectMapper JSON = new ObjectMapper();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITION_COUNT)
          .withReplicationFactor(3)
          .build();

  @AutoClose private CamundaClient client;
  private ClusterRebalanceRestClient rebalanceClient;

  @BeforeEach
  void setup() {
    // broker 1 is stopped/restarted to force an imbalance, so use another broker's client
    client = cluster.brokers().get(MemberId.from("0")).newClientBuilder().build();
    rebalanceClient = ClusterRebalanceRestClient.of(cluster.availableGateway());
  }

  @Test
  void shouldRebalanceLeadershipAndResumeProcessing() {
    // given
    forceBadLeaderDistribution();
    assertThat(hasBadLeaderDistribution()).isTrue();
    final var processInstanceKeys =
        createInstanceWithAJobOnAllPartitions(client, JOB_TYPE, PARTITION_COUNT);

    // when
    assertAccepted(triggerRebalance());

    // then
    awaitCompletedRebalance();
    awaitBalancedTopology();
    assertThatAllJobsCanBeCompleted(processInstanceKeys, client, JOB_TYPE);
  }

  private void assertAccepted(final Response response) {
    final String body = readBody(response);
    assertThat(response.status())
        .as("rebalance response: %s", body)
        .isEqualTo(HttpURLConnection.HTTP_ACCEPTED);
  }

  private Response triggerRebalance() {
    return rebalanceClient.triggerRebalance();
  }

  private Response getRebalance() {
    return rebalanceClient.getRebalance();
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

  private void awaitCompletedRebalance() {
    Awaitility.await("the rebalance completes with at least one transferred partition")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final var response = getRebalance();
              final String responseBody = readBody(response);
              assertThat(response.status())
                  .as("rebalance status response: %s", responseBody)
                  .isEqualTo(HttpURLConnection.HTTP_OK);

              final JsonNode body = JSON.readTree(responseBody);
              assertThat(body.has("runningRebalance"))
                  .as("runningRebalance field present: %s", responseBody)
                  .isTrue();
              assertThat(body.get("runningRebalance").isNull())
                  .as("no rebalance still running: %s", responseBody)
                  .isTrue();

              final JsonNode lastCompleted = body.get("lastCompletedRebalance");
              assertThat(lastCompleted)
                  .as("a completed rebalance is present: %s", responseBody)
                  .isNotNull();
              assertThat(lastCompleted.isNull())
                  .as("a completed rebalance is present: %s", responseBody)
                  .isFalse();

              assertThat(lastCompleted.path("result").asText())
                  .as("rebalance result: %s", responseBody)
                  .isEqualTo("COMPLETED");

              final var partitions = lastCompleted.path("partitions");
              assertThat(partitions.isArray() && partitions.size() > 0)
                  .as("rebalance partitions present: %s", responseBody)
                  .isTrue();
              final boolean anyTransferred =
                  StreamSupport.stream(partitions.spliterator(), false)
                      .anyMatch(
                          partition -> "TRANSFERRED".equals(partition.path("result").asText()));
              assertThat(anyTransferred)
                  .as("at least one partition was transferred: %s", responseBody)
                  .isTrue();
            });
  }

  private void awaitBalancedTopology() {
    Awaitility.await("the final leader distribution is round-robin across brokers 0, 1, and 2")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .hasLeaderForPartition(1, 0)
                  .hasLeaderForPartition(2, 1)
                  .hasLeaderForPartition(3, 2);
            });
  }

  @SuppressWarnings("resource")
  private void forceBadLeaderDistribution() {
    if (hasGoodLeaderDistribution()) {
      final var brokerId = MemberId.from("1");
      final var stoppedBroker = cluster.brokers().get(brokerId).stop();
      LOG.debug("Broker stopped");
      waitForBadLeaderDistribution();
      LOG.debug("Bad distribution of partition: waiting for the broker to be ready");
      stoppedBroker.start().await(TestHealthProbe.READY);

      stoppedBroker.awaitCompleteTopology(
          cluster.brokers().size(),
          cluster.partitionsCount(),
          cluster.replicationFactor(),
          Duration.ofMinutes(1));
    }
  }

  private void waitForBadLeaderDistribution() {
    Awaitility.await("at least one broker is leader for more than one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasBadLeaderDistribution);
  }

  private boolean hasBadLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .anyMatch(
            brokerInfo ->
                brokerInfo.getPartitions().stream().filter(PartitionInfo::isLeader).count() > 1);
  }

  private boolean hasGoodLeaderDistribution() {
    return client.newTopologyRequest().send().join().getBrokers().stream()
        .allMatch(
            brokerInfo ->
                brokerInfo.getPartitions().stream().filter(PartitionInfo::isLeader).count() == 1);
  }
}
