/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

final class RebalancingEndpointIT {
  private final Network network = Network.newNetwork();

  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(3)
          .withReplicationFactor(3)
          .withNetwork(network)
          .build();

  private ZeebeClient client;

  @BeforeEach
  void setup() {
    cluster.start();
    client = cluster.newClientBuilder().build();
  }

  @AfterEach
  void teardown() {
    cluster.stop();
  }

  @Test
  void shouldRebalanceCluster() throws IOException, InterruptedException {
    // given
    forceBadLeaderDistribution();

    // when
    triggerRebalancing();

    // then
    reachesGoodLeaderDistribution();
  }

  private void triggerRebalancing() throws IOException, InterruptedException {
    final var gateway = cluster.getGateways().values().stream().findFirst().orElseThrow();
    final var monitoringAddress = gateway.getExternalMonitoringAddress();
    final var httpClient = HttpClient.newHttpClient();
    final var request =
        HttpRequest.newBuilder()
            .POST(BodyPublishers.noBody())
            .uri(URI.create("http://" + monitoringAddress + "/actuator/rebalance"))
            .build();
    final var response = httpClient.send(request, BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private void forceBadLeaderDistribution() {
    // Only restart a broker if we currently have a good distribution.
    // Otherwise, restarting might accidentally create a good distribution and
    // `waitForBadLeaderDistribution` times out.
    if (hasGoodLeaderDistribution()) {
      cluster.getBrokers().get(1).stop();
      cluster.getBrokers().get(1).start();
      waitForBadLeaderDistribution();
    }
  }

  private void waitForBadLeaderDistribution() {
    Awaitility.await("At least one broker is leader for more than one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasBadLeaderDistribution);
  }

  private void reachesGoodLeaderDistribution() {
    Awaitility.await("All brokers are leader for exactly one partition")
        .timeout(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .until(this::hasGoodLeaderDistribution);
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
