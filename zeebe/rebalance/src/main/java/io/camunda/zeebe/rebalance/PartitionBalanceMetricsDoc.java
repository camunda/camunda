/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Whether leadership is where the cluster configuration wants it. */
public enum PartitionBalanceMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Whether a partition is led by the member the cluster configuration gives the highest priority
   * for it: {@code 1} when it is, {@code 0} when it is led by another member or by nobody at all.
   */
  PARTITION_BALANCED {
    @Override
    public String getName() {
      return "zeebe.cluster.partition.balanced";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "1 when a partition is led by its highest-priority member, 0 otherwise";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, PartitionKeyNames.PHYSICAL_TENANT};
    }
  }
}
