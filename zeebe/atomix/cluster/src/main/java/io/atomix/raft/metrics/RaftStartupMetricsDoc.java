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
public enum RaftStartupMetricsDoc implements ExtendedMeterDocumentation {
  /** Time taken to bootstrap the partition server (in ms) */
  BOOTSTRAP_DURATION {
    @Override
    public String getName() {
      return "atomix.partition.server.bootstrap.time";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Time taken to bootstrap the partition server (in ms)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RaftKeyNames.PARTITION_GROUP, PartitionKeyNames.PARTITION};
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }
  },
  /* Time taken for the partition server to join (in ms) */
  JOIN_DURATION {
    @Override
    public String getName() {
      return "atomix.partition.server.join.time";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {RaftKeyNames.PARTITION_GROUP, PartitionKeyNames.PARTITION};
    }

    @Override
    public String getDescription() {
      return "Time taken for the partition server to join (in ms)";
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }
  }
}
