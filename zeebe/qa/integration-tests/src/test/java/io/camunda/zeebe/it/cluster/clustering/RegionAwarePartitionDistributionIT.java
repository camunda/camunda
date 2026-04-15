/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*


* Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
* one or more contributor license agreements. See the NOTICE file distributed
* with this work for additional information regarding copyright ownership.
* Licensed under the Camunda License 1.0. You may not use this file
* except in compliance with the Camunda License 1.0.
*/
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.Partitioning;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.partitioning.RegionAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.RegionCfg;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the {@link
 * io.camunda.zeebe.dynamic.config.util.RegionAwarePartitionDistributor} scheme.
 *
 * <p>Starts a single-region cluster and verifies:
 *
 * <ol>
 *   <li>The cluster forms successfully — proving that all brokers agree on the same composite
 *       {@code "region-nodeId"} member IDs.
 *   <li>Each broker has the expected region name propagated into its runtime configuration.
 * </ol>
 */
final class RegionAwarePartitionDistributionIT {

  private static final String REGION = "us-east1";
  private static final int BROKERS = 3;

  @AutoClose private TestCluster cluster;

  @Test
  void shouldFormClusterAndPropagateRegionConfig() {
    // given — single-region cluster: all 3 brokers in us-east1
    cluster =
        TestCluster.builder()
            .withBrokersCount(BROKERS)
            .withEmbeddedGateway(true)
            .withPartitionsCount(BROKERS)
            .withReplicationFactor(BROKERS)
            .withBrokerConfig(broker -> configureRegionAwareBroker(broker, REGION, BROKERS))
            .build();

    // when
    cluster.start();
    final var test = cluster.awaitCompleteTopology();

    final CamundaClient build = cluster.newClientBuilder().build();

    final Topology execute = build.newTopologyRequest().execute();

    // then — verify the region setting was correctly propagated to each broker's runtime
    // config.
    // Successful cluster formation already proves that the composite "region-nodeId" MemberIds
    // are
    // consistent (otherwise the Raft group would never elect a leader and the topology check
    // would
    // time out).
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var clusterCfg = broker.bean(BrokerBasedProperties.class).getCluster();
              assertThat(clusterCfg.getRegion())
                  .as(
                      "broker %d should have region '%s' in its runtime config",
                      clusterCfg.getNodeId(), REGION)
                  .isEqualTo(REGION);
            });
  }

  private static void configureRegionAwareBroker(
      final TestStandaloneBroker broker, final String regionName, final int brokerCount) {
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getCluster().setRegion(regionName);

          final var regionCfg = new RegionCfg();
          regionCfg.setName(regionName);
          regionCfg.setNumberOfBrokers(brokerCount);
          regionCfg.setNumberOfReplicas(brokerCount);
          regionCfg.setPriority(1000);

          final var regionAwareCfg = new RegionAwareCfg();
          regionAwareCfg.setRegions(List.of(regionCfg));

          final var partitioning = new Partitioning();
          partitioning.setScheme(Partitioning.Scheme.REGION_AWARE);
          partitioning.setRegionAware(regionAwareCfg);
          cfg.getCluster().setPartitioning(partitioning);
        });
  }
}
