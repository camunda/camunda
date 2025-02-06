/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum SnapshotReplicationMetricsDoc implements ExtendedMeterDocumentation {
  /** Count of ongoing snapshot replication */
  COUNT {
    @Override
    public String getName() {
      return "atomix.snapshot.replication.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Count of ongoing snapshot replication";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, RaftKeyNames.PARTITION_GROUP};
    }
  },
  /** Approximate duration of replication in milliseconds */
  DURATION {
    @Override
    public String getName() {
      return "atomix.snapshot.replication.duration.milliseconds";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Approximate duration of replication in milliseconds";
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, RaftKeyNames.PARTITION_GROUP};
    }
  }
}
