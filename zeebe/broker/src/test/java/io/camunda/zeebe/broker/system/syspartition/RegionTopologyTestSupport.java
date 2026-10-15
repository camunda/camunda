/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneRole;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the broker configuration of a 3x3x1 cluster: two data regions and a control region. */
public final class RegionTopologyTestSupport {
  public static final String REGION_A = "region-a";
  public static final String REGION_B = "region-b";
  public static final String CONTROL = "control";

  private RegionTopologyTestSupport() {}

  /**
   * @param systemPartitionReplicationFactor the system partition's replication factor, or null for
   *     every broker
   */
  public static BrokerCfg threeByThreeByOne(
      final @Nullable Integer systemPartitionReplicationFactor) {
    final var brokerCfg = new BrokerCfg();
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    partitioning.setScheme(Scheme.ZONE_AWARE);
    partitioning.setZoneAware(
        new ZoneAwareCfg(
            List.of(
                new ZoneCfg(REGION_A, 3, 2, 1000),
                new ZoneCfg(REGION_B, 3, 2, 500),
                new ZoneCfg(CONTROL, 1, 0, 1, ZoneRole.CONTROL))));
    brokerCfg.getCluster().setClusterSize(7);
    brokerCfg.getCluster().setReplicationFactor(4);
    brokerCfg.getCluster().setZone(REGION_A);
    brokerCfg.getCluster().getSystemPartition().setEnabled(true);
    brokerCfg
        .getCluster()
        .getSystemPartition()
        .setReplicationFactor(systemPartitionReplicationFactor);
    return brokerCfg;
  }
}
