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
public enum RaftServiceMetricsDoc implements ExtendedMeterDocumentation {
  /** Time spend to compact */
  COMPACTION_TIME {
    @Override
    public String getName() {
      return "atomix.compaction.time.ms";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spend to compact";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RaftKeyNames.PARTITION_GROUP, PartitionKeyNames.PARTITION};
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }
  }
}
