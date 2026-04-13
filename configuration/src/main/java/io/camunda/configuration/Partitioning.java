/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.broker.system.configuration.partitioning.RegionAwareCfg;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Partitioning {
  private static final String PREFIX = "camunda.cluster.partitioning";
  private static final Set<String> LEGACY_SCHEME_PROPERTIES =
      Set.of("zeebe.broker.experimental.partitioning.scheme");

  /**
   * The default partitioning {@link Scheme} is the {@link Scheme#ROUND_ROBIN} scheme. This is
   * required for backwards compatibility, but it's also a good default for now as there is no
   * better alternative in most cases.
   */
  private static final Scheme DEFAULT_SCHEME = Scheme.ROUND_ROBIN;

  /**
   * The partitioning scheme used for assigning partitions to nodes. Defaults to {@link
   * Scheme#ROUND_ROBIN}.
   */
  private Scheme scheme = DEFAULT_SCHEME;

  /**
   * The list of fixed partition configurations for this partitioning setup. Initialized as an
   * empty, list by default. Used when the {@link Scheme#FIXED} partitioning scheme is selected.
   */
  private List<FixedPartition> fixed = Collections.emptyList();

  /**
   * The region-aware partitioning configuration. Used when the {@link Scheme#REGION_AWARE}
   * partitioning scheme is selected.
   *
   * <p>Maps region names to their per-region configuration (number of brokers, replicas, and
   * priority). The insertion order of the map is significant: broker member IDs are assigned
   * sequentially per region in iteration order.
   */
  private RegionAwareCfg regionAware = new RegionAwareCfg();

  public Partitioning() {}

  public Partitioning(final Scheme scheme, final List<FixedPartition> fixed) {
    this.scheme = scheme;
    this.fixed = fixed;
  }

  public Scheme getScheme() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".scheme",
        scheme,
        Scheme.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SCHEME_PROPERTIES);
  }

  public void setScheme(final Scheme scheme) {
    this.scheme = scheme;
  }

  public List<FixedPartition> getFixed() {
    return fixed;
  }

  public void setFixed(final List<FixedPartition> fixed) {
    this.fixed = fixed;
  }

  public RegionAwareCfg getRegionAware() {
    return regionAware;
  }

  public void setRegionAware(final RegionAwareCfg regionAware) {
    this.regionAware = regionAware;
  }

  public enum Scheme {
    FIXED,
    ROUND_ROBIN,
    REGION_AWARE;
  }
}
