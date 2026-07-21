/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Metrics for coordinated leadership transfer (rebalancing). */
@SuppressWarnings("NullableProblems")
public enum RebalanceMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * How long a partition stayed paused (declining writes, processing paused) while its current
   * leader waited for the desired leader to catch up during a coordinated leadership transfer.
   * Useful for calibrating the rebalancing configuration.
   */
  PARTITION_PAUSE_DURATION {
    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getName() {
      return "zeebe.cluster.rebalance.partition.pause.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Duration a partition was paused during a coordinated leadership transfer";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, PartitionKeyNames.PHYSICAL_TENANT};
    }
  },

  /**
   * Whether a partition is currently paused for a coordinated leadership transfer: {@code 1} only
   * while write admission is frozen <em>and</em> processing is paused for the transfer, {@code 0}
   * otherwise. Distinct from {@code zeebe.stream.processor.state}, which reports processing paused
   * for any reason.
   */
  PARTITION_PAUSED {
    @Override
    public String getName() {
      return "zeebe.cluster.rebalance.partition.paused";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "1 while a partition is paused for a coordinated leadership transfer, 0 otherwise";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, PartitionKeyNames.PHYSICAL_TENANT};
    }
  }
}
