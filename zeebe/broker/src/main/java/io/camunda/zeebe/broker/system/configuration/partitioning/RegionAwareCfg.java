/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.partitioning;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.LinkedHashMap;

/**
 * Top-level configuration for the {@link Scheme#REGION_AWARE} partitioning scheme.
 *
 * <p>Maps region names to their respective {@link RegionCfg}. The insertion order of this map is
 * significant: broker {@link io.atomix.cluster.MemberId}s are assigned sequentially per region in
 * iteration order. For example, given regions {@code us-east1} (2 brokers) then {@code us-west1}
 * (2 brokers), the member IDs will be {@code us-east1-0}, {@code us-east1-1}, {@code us-west1-0},
 * {@code us-west1-1}.
 *
 * <p>The map <strong>must</strong> be a {@link LinkedHashMap} to preserve YAML insertion order.
 * This is enforced via the {@link JsonDeserialize} annotation.
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
 *           us-east1:
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 1000
 *           us-west1:
 *             numberOfBrokers: 2
 *             numberOfReplicas: 2
 *             priority: 500
 *           euro-east1:
 *             numberOfBrokers: 1
 *             numberOfReplicas: 1
 *             priority: 10
 * }</pre>
 */
public final class RegionAwareCfg {

  @JsonDeserialize(as = LinkedHashMap.class)
  private LinkedHashMap<String, RegionCfg> regions = new LinkedHashMap<>();

  public LinkedHashMap<String, RegionCfg> getRegions() {
    return regions;
  }

  public void setRegions(final LinkedHashMap<String, RegionCfg> regions) {
    this.regions = regions;
  }

  @Override
  public String toString() {
    return "RegionAwareCfg{regions=" + regions + '}';
  }
}
