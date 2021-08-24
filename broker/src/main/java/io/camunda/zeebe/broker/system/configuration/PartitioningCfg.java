/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import java.util.ArrayList;
import java.util.List;

/**
 * The partitioning configuration allow configuring experimental settings related to partitioning.
 *
 * <p>At the moment, it lets users configure the scheme - that is, how partitions are distributed
 * across the brokers. The default scheme is currently {@link Scheme#ROUND_ROBIN}.
 *
 * <p>When using {@link Scheme#FIXED}, a map of brokers to a list of partitions should be specified
 * under {@link #fixed}. This map takes keys as the broker node IDs, with values as a list of
 * partition IDs. The mapping must be exhaustive, meaning all brokers should appear, and all
 * partitions should be specified with the appropriate replication factor.
 */
public final class PartitioningCfg {

  /**
   * The default partitioning {@link Scheme} is the {@link Scheme#ROUND_ROBIN} scheme. This is
   * required for backwards compatibility, but it's also a good default for now as there is no
   * better alternative in most cases.
   */
  private static final Scheme DEFAULT_SCHEME = Scheme.ROUND_ROBIN;

  private Scheme scheme = DEFAULT_SCHEME;
  private List<FixedPartitionCfg> fixed = new ArrayList<>();

  public Scheme getScheme() {
    return scheme;
  }

  public void setScheme(final Scheme scheme) {
    this.scheme = scheme;
  }

  public List<FixedPartitionCfg> getFixed() {
    return fixed;
  }

  public void setFixed(final List<FixedPartitionCfg> fixed) {
    this.fixed = fixed;
  }

  @Override
  public String toString() {
    return "PartitioningCfg{" + "scheme=" + scheme + ", fixed=" + fixed + '}';
  }
}
