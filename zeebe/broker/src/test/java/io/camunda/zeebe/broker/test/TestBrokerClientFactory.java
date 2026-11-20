/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.awaitility.Awaitility;

public final class TestBrokerClientFactory {
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);

  public static BrokerClient createBrokerClient(
      final AtomixCluster atomixCluster, final ActorScheduler actorScheduler) {
    final var meterRegistry = new SimpleMeterRegistry();
    final var topologyManager =
        new BrokerTopologyManagerImpl(
            () -> atomixCluster.getMembershipService().getMembers(),
            new BrokerClientTopologyMetrics(meterRegistry));
    actorScheduler.submitActor(topologyManager).join();
    atomixCluster.getMembershipService().addListener(topologyManager);

    final var client =
        new BrokerClientImpl(
            DEFAULT_REQUEST_TIMEOUT,
            atomixCluster.getMessagingService(),
            atomixCluster.getEventService(),
            actorScheduler,
            topologyManager,
            new BrokerClientRequestMetrics(meterRegistry));
    client.start().forEach(ActorFuture::join);
    return client;
  }

  /** Waits until the broker client knows every broker and every partition with its leader. */
  public static void awaitCompleteTopology(
      final BrokerClient brokerClient,
      final int clusterSize,
      final int partitionsCount,
      final int replicationFactor) {
    Awaitility.await("until we have a complete topology")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final var topology = brokerClient.getTopologyManager().getTopology();
              assertThat(topology.isInitialized()).isTrue();
              assertThat(topology.getBrokers()).hasSize(clusterSize);
              assertThat(topology.getPartitions()).hasSize(partitionsCount);
              for (final int partition : topology.getPartitions()) {
                assertThat(topology.getLeaderForPartition(partition)).isNotNull();
                assertThat(topology.getFollowersForPartition(partition))
                    .hasSize(replicationFactor - 1);
              }
            });
  }
}
