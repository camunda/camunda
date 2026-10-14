/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Map;
import java.util.Set;

/**
 * The load counted across the cluster over a window.
 *
 * @param ratesPerSecond the sum of each measure's rate over the brokers that reported every measure
 *     at both ends of the window
 * @param unaccounted brokers that missed either end of the window, or restarted in between, so
 *     {@code ratesPerSecond} may understate the load
 */
public record ClusterLoad(Map<LoadMeasure, Double> ratesPerSecond, Set<MemberId> unaccounted) {
  public ClusterLoad {
    ratesPerSecond = Map.copyOf(ratesPerSecond);
    unaccounted = Set.copyOf(unaccounted);
  }

  public boolean isComplete() {
    return unaccounted.isEmpty();
  }
}
