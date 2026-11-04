/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import io.camunda.zeebe.util.ecv.CapabilitySet;
import io.camunda.zeebe.util.ecv.EnabledCapabilitiesView;
import io.camunda.zeebe.util.ecv.PartitionCapabilities;
import io.camunda.zeebe.util.ecv.PartitionCapabilities.Progress;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands each partition of this broker what its engine needs for the ECV commit, and keeps the
 * progress of each partition that this broker leads, for the progress query (design section 4).
 */
public final class PartitionCapabilitiesRegistry {
  private final CapabilitySet supported;
  private final EnabledCapabilitiesView view;
  private final Map<String, Map<Integer, Progress>> leaderProgress = new ConcurrentHashMap<>();

  public PartitionCapabilitiesRegistry(
      final CapabilitySet supported, final EnabledCapabilitiesView view) {
    this.supported = supported;
    this.view = view;
  }

  public PartitionCapabilities forPartition(final String physicalTenantId, final int partitionId) {
    return new PartitionCapabilities() {
      @Override
      public CapabilitySet supported() {
        return supported;
      }

      @Override
      public EnabledCapabilitiesView view() {
        return view;
      }

      @Override
      public void onProgress(final Progress progress) {
        leaderProgress
            .computeIfAbsent(physicalTenantId, ignored -> new ConcurrentHashMap<>())
            .put(partitionId, progress);
      }

      @Override
      public void onLeadershipLost() {
        final var partitions = leaderProgress.get(physicalTenantId);
        if (partitions != null) {
          partitions.remove(partitionId);
        }
      }
    };
  }

  /** Returns the progress of each partition that this broker leads, by physical tenant. */
  public Map<String, Map<Integer, Progress>> leaderProgress() {
    final var copy = new TreeMap<String, Map<Integer, Progress>>();
    leaderProgress.forEach((tenant, partitions) -> copy.put(tenant, new TreeMap<>(partitions)));
    return copy;
  }
}
