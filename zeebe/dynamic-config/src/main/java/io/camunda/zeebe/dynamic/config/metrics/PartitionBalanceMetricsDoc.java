/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Whether leadership is where the cluster configuration wants it. */
@SuppressWarnings("NullableProblems")
public enum PartitionBalanceMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Whether a partition is led by the member the cluster configuration gives the highest priority
   * for it: {@code 1} when it is, {@code 0} when it is led by another member or by nobody at all.
   *
   * <p>Published by every member, from the two views every member already holds - who leads each
   * partition, and who the configuration wants to. Reporting it from one member instead would tie
   * the answer to that member being up, and a member being down is the commonest reason for the
   * answer to be no.
   *
   * <p>The two views are gossiped separately and converge independently, so members disagree about
   * a partition for as long as it takes both to settle. Reduce per partition before reducing across
   * partitions - {@code avg(min by (physicalTenant, partition) (...))} - so that the fraction does
   * not move with the number of members reporting, and take the pessimistic reduction: a member
   * wrongly reporting balanced would hide an unbalanced partition, whereas one wrongly reporting
   * unbalanced is only noise.
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
