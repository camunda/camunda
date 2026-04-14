/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.configuration.Partitioning;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.partitioning.RegionAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.RegionCfg;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Integration test verifying that brokers in different regions can form a cluster and communicate
 * with each other.
 *
 * <p>Sets up a two-region cluster where each region has a single broker:
 *
 * <ul>
 *   <li>Region {@code "us-east1"}: global nodeId=0, local nodeId=0, composite member ID {@code
 *       "us-east1-0"}
 *   <li>Region {@code "us-west1"}: global nodeId=1, local nodeId=0, composite member ID {@code
 *       "us-west1-0"} — started with the east broker's cluster address as its initial contact point
 * </ul>
 *
 * <p>Node IDs are assigned as globally unique integers across the cluster. {@link
 * io.camunda.zeebe.broker.clustering.ClusterConfigFactory} converts a broker's global nodeId to a
 * local (region-scoped) nodeId by subtracting the cumulative broker count of all preceding regions:
 * east (offset=0) produces {@code "us-east1-0"}, west (global=1, offset=1) produces {@code
 * "us-west1-0"}. {@link io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator}
 * always uses local IDs 0..N-1 per region, so the two member ID namespaces are consistent.
 *
 * <p>The test verifies:
 *
 * <ol>
 *   <li>The cluster forms successfully — both brokers see each other and the single partition
 *       elects a leader across the two regions.
 *   <li>The topology API exposes the composite member IDs to clients.
 *   <li>Each broker has the correct region propagated into its runtime configuration.
 * </ol>
 */
final class MultiRegionClusterIT {

  private static final String REGION_EAST = "us-east1";
  private static final String REGION_WEST = "us-west1";

  @AutoClose private TestStandaloneBroker brokerEast;
  @AutoClose private TestStandaloneBroker brokerWest;

  @Test
  void shouldFormClusterAcrossRegions() {
    // given — shared region config listing both regions, one broker each
    final var regionAwareCfg = buildRegionAwareCfg();

    // east broker uses global nodeId=0 — start it first as the SWIM seed node
    brokerEast = createBroker(0, REGION_EAST, regionAwareCfg, List.of());
    CompletableFuture.runAsync(() -> brokerEast.start());

    // west broker uses global nodeId=1 — contact points set to east's cluster address
    brokerWest =
        createBroker(
            0, REGION_WEST, regionAwareCfg, List.of(brokerEast.address(TestZeebePort.CLUSTER)));
    CompletableFuture.runAsync(() -> brokerWest.start());

    Awaitility.await().until(() -> brokerWest.isStarted());

    // when — wait for both brokers to form the cluster and elect a leader for partition 1
    try (final var client = brokerEast.newClientBuilder().build()) {
      Awaitility.await("cluster of 2 brokers to form across regions")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                final var topology = client.newTopologyRequest().send().join();
                assertThat(topology.getBrokers()).hasSize(2);
                assertThat(topology.getBrokers())
                    .flatExtracting(BrokerInfo::getPartitions)
                    .filteredOn(PartitionInfo::isLeader)
                    .extracting(PartitionInfo::getPartitionId)
                    .containsExactly(1);
              });

      // then — composite member IDs (region-localNodeId) are exposed to clients
      final var topology = client.newTopologyRequest().execute();
      assertThat(topology.getBrokers())
          .extracting(BrokerInfo::getNodeId)
          .containsExactlyInAnyOrder(REGION_EAST + "-0", REGION_WEST + "-0");
    }

    // and — each broker carries the correct region in its runtime config
    assertThat(brokerEast.bean(BrokerBasedProperties.class).getCluster().getRegion())
        .isEqualTo(REGION_EAST);
    assertThat(brokerWest.bean(BrokerBasedProperties.class).getCluster().getRegion())
        .isEqualTo(REGION_WEST);
  }

  /**
   * Creates a broker configured for region-aware mode.
   *
   * <p>Node IDs are globally unique integers: east uses {@code nodeId=0}, west uses {@code
   * nodeId=1}. {@link io.camunda.zeebe.broker.clustering.ClusterConfigFactory} derives the local
   * node ID by subtracting the cumulative broker count of preceding regions, producing {@code
   * "us-east1-0"} and {@code "us-west1-0"} — consistent with what {@link
   * io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator} generates using
   * per-region 0-indexed IDs.
   */
  private static TestStandaloneBroker createBroker(
      final int nodeId,
      final String region,
      final RegionAwareCfg regionAwareCfg,
      final List<String> contactPoints) {
    return new TestStandaloneBroker()
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setNodeId(nodeId);
              cfg.getCluster().setRegion(region);
              cfg.getCluster().setSize(2);
              cfg.getCluster().setPartitionCount(1);
              cfg.getCluster().setReplicationFactor(2);
              if (!contactPoints.isEmpty()) {
                cfg.getCluster().setInitialContactPoints(contactPoints);
              }

              final var partitioning = new Partitioning();
              partitioning.setScheme(Partitioning.Scheme.REGION_AWARE);
              partitioning.setRegionAware(regionAwareCfg);
              cfg.getCluster().setPartitioning(partitioning);
            });
  }

  /**
   * Builds the shared {@link RegionAwareCfg} listing both regions with one broker and replica each.
   * East has higher priority so it hosts the preferred partition leader.
   */
  private static RegionAwareCfg buildRegionAwareCfg() {
    final var east = new RegionCfg();
    east.setNumberOfBrokers(1);
    east.setNumberOfReplicas(1);
    east.setPriority(1000);

    final var west = new RegionCfg();
    west.setNumberOfBrokers(1);
    west.setNumberOfReplicas(1);
    west.setPriority(500);

    final var regions = new LinkedHashMap<String, RegionCfg>();
    regions.put(REGION_EAST, east);
    regions.put(REGION_WEST, west);

    final var cfg = new RegionAwareCfg();
    cfg.setRegions(regions);
    return cfg;
  }
}
