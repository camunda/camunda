/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ClientException;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class LeaderWarmupIT {

  private static final double COMPLETED = 2;
  private static final double CANCELLED = 4;
  private static final double SKIPPED = 5;

  @AutoClose private TestCluster cluster;

  @Test
  void shouldWarmUpOnlyTheBrokerThatLeadsNothing() {
    // given
    cluster =
        TestCluster.builder()
            .withBrokersCount(2)
            .withEmbeddedGateway(true)
            .withPartitionsCount(1)
            .withReplicationFactor(2)
            .withBrokerConfig(
                broker ->
                    broker
                        .withProperty("zeebe.broker.experimental.leaderWarmup.enabled", true)
                        .withProperty("zeebe.broker.experimental.leaderWarmup.startDelay", "0s")
                        .withProperty(
                            "zeebe.broker.experimental.leaderWarmup.processInstances", 300))
            .build();

    // when
    cluster.start().awaitCompleteTopology();

    // then
    final var leader = cluster.leaderForPartition(1);
    final var follower =
        cluster.brokers().values().stream().filter(b -> b != leader).findFirst().orElseThrow();
    Awaitility.await("follower completes its warm-up")
        .atMost(Duration.ofMinutes(2))
        .until(() -> warmupState(follower) == COMPLETED);
    assertThat(warmupState(leader)).isIn(SKIPPED, CANCELLED);
    assertThat(
            Path.of(follower.unifiedConfig().getData().getPrimaryStorage().getDirectory())
                .resolve("leader-warmup"))
        .doesNotExist();

    try (final var client = cluster.newClientBuilder().build()) {
      assertThatThrownBy(
              () ->
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId("leader-warmup")
                      .latestVersion()
                      .send()
                      .join())
          .isInstanceOf(ClientException.class)
          .hasMessageContaining("leader-warmup");
    }
  }

  private static double warmupState(final TestStandaloneBroker broker) {
    return PrometheusActuator.of(broker)
        .metrics()
        .lines()
        .filter(line -> line.startsWith("zeebe_leader_warmup_state"))
        .map(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
        .findFirst()
        .orElse(-1.0);
  }
}
