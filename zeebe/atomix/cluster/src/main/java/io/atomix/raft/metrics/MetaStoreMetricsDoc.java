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
public enum MetaStoreMetricsDoc implements ExtendedMeterDocumentation {
  /** Time it takes to update the last flushed index */
  LAST_FLUSHED_INDEX {
    @Override
    public String getName() {
      return "atomix.last.flushed.index.update";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time it takes to update the last flushed index";
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
