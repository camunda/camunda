/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Tags;

@SuppressWarnings("NullableProblems")
public enum PartitionKeyNames implements KeyName {
  /** The ID of the physical tenant associated with the metric. Unique within the cluster. */
  PHYSICAL_TENANT {
    @Override
    public String asString() {
      return "physicalTenant";
    }
  },

  /**
   * The ID of the partition associated with the metric. Unique within one {@link #PHYSICAL_TENANT}.
   */
  PARTITION {
    @Override
    public String asString() {
      return "partition";
    }
  };

  public static Tags tags(final String physicalTenantId, final int partitionId) {
    return Tags.of(
        PHYSICAL_TENANT.asString(),
        physicalTenantId,
        PARTITION.asString(),
        String.valueOf(partitionId));
  }

  /**
   * Returns the usual partition tags but with placeholder values to indicate that this metric is
   * not associated with any specific partition.
   */
  public static Tags noPartition() {
    return Tags.of(PHYSICAL_TENANT.asString(), "none", PARTITION.asString(), "none");
  }
}
