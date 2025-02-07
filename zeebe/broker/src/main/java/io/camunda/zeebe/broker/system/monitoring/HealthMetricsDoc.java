/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;

@SuppressWarnings("NullableProblems")
public enum HealthMetricsDoc implements ExtendedMeterDocumentation {
  /** Shows current health of the partition (1 = healthy, 0 = unhealthy, -1 = dead) */
  HEALTH {
    @Override
    public String getName() {
      return "zeebe.health";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Shows current health of the partition (1 = healthy, 0 = unhealthy, -1 = dead)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * Shows current health of a specific component. This is useful as the health of a partition in
   * general is the sum of the health of all its components. If any of them are not healthy, then
   * the partition is not either.
   *
   * <p>Partitions themselves are health components for a broker, so the same logic applies to the
   * health of a broker.
   *
   * <p>Health is defined as:
   *
   * <ul>
   *   <li>Unknown: 0
   *   <li>Healthy: 1
   *   <li>Unhealthy: -1
   *   <li>Dead: -2
   * </ul>
   */
  NODES {
    @Override
    public String getName() {
      return "zeebe.broker.health.nodes";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Shows current health of a specific component (0 = unknown, 1 = healthy, -1 = unhealthy, -2 = dead)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return NodesKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  /** Allows grouping the component health by ID and their hierarchy. */
  enum NodesKeyNames implements KeyName {
    /** The ID of the component node */
    ID {
      @Override
      public String asString() {
        return "id";
      }
    },

    /**
     * Represents the ownership hierarchy of the component; the component itself is always the last
     * segment of the path, and is owned by the previous one, which is one by the previous one,
     * etc., recursively.
     *
     * <p>The health of a segment is always the sum of the all of its children. If one segment is
     * not healthy (unhealthy or dead), and the whole path upwards is marked the same.
     */
    PATH {
      @Override
      public String asString() {
        return "path";
      }
    }
  }
}
