/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The partition distributor configuration stored in the cluster's agreed state. Keeping this in the
 * gossiped topology ensures that late-joining or recovering brokers use the same distributor as the
 * rest of the cluster rather than falling back to static configuration.
 */
@NullMarked
public sealed interface PartitionDistributorConfig {

  record RoundRobinConfig() implements PartitionDistributorConfig {}

  record ZoneAwareConfig(List<ZoneSpec> zones) implements PartitionDistributorConfig {
    public ZoneAwareConfig {
      zones = List.copyOf(zones);
    }
  }

  /**
   * Marker for a fixed (statically-configured) distribution. The actual partition→member mapping
   * is not serialized; the broker resolves it from its local static configuration at runtime.
   */
  record FixedConfig() implements PartitionDistributorConfig {}

  /**
   * State-layer representation of a zone's participation in the cluster.
   *
   * @param name the zone name (e.g. {@code "us-east1"})
   * @param numberOfReplicas how many replicas of each partition are placed in this zone
   * @param priority the zone's preferred-leader ranking; higher values are preferred
   */
  record ZoneSpec(String name, int numberOfReplicas, int priority) {
    public ZoneSpec {
      if (name.isEmpty()) {
        throw new IllegalArgumentException("ZoneSpec: name must not be empty");
      }
      if (numberOfReplicas <= 0) {
        throw new IllegalArgumentException(
            "ZoneSpec: numberOfReplicas must be >= 1, got " + numberOfReplicas);
      }
      if (priority <= 0) {
        throw new IllegalArgumentException("ZoneSpec: priority must be > 0, got " + priority);
      }
    }
  }
}
