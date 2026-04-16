/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.partitioning;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level configuration for the {@link Scheme#REGION_AWARE} partitioning scheme.
 *
 * <p>Contains a list of {@link RegionCfg} entries, each identified by its {@link
 * RegionCfg#getName() name}. The list order is significant: broker {@link
 * io.atomix.cluster.MemberId}s are assigned sequentially per region in iteration order. For
 * example, given regions {@code us-east1} (2 brokers) then {@code us-west1} (2 brokers), the member
 * IDs will be {@code us-east1-0}, {@code us-east1-1}, {@code us-west1-0}, {@code us-west1-1}.
 *
 * <p>Using a list (rather than a map keyed by region name) makes it straightforward to override
 * individual region properties via indexed environment variables, e.g.:
 *
 * <pre>
 * CAMUNDA_CLUSTER_PARTITIONING_REGIONAWARE_REGIONS_0_NAME=us-east1
 * CAMUNDA_CLUSTER_PARTITIONING_REGIONAWARE_REGIONS_0_NUMBEROFBROKERS=2
 * </pre>
 *
 * <p>Example YAML configuration:
 *
 * <pre>{@code
 * camunda:
 *   cluster:
 *     size: 5
 *     replication-factor: 5
 *     region: us-east1         # set per broker; env: CAMUNDA_CLUSTER_REGION
 *     partitioning:
 *       scheme: REGION_AWARE
 *       region-aware:
 *         regions:
 *           - name: us-east1
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 1000
 *           - name: us-west1
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 500
 *           - name: euro-east1
 *             numberOfBrokers: 1
 *             numberOfReplicas: 1
 *             priority: 10
 * }</pre>
 */
public final class RegionAwareCfg {

  private List<RegionCfg> regions = new ArrayList<>();

  public List<RegionCfg> getRegions() {
    return regions;
  }

  public void setRegions(final List<RegionCfg> regions) {
    this.regions = regions;
  }

  @Override
  public String toString() {
    return "RegionAwareCfg{regions=" + regions + '}';
  }
}
