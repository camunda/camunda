/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@SuppressWarnings("NullableProblems")
public enum ColumnFamilyMetricsDoc implements ExtendedMeterDocumentation {
  /** Latency of RocksDB operations per column family */
  LATENCY {
    private static final KeyName[] KEYS =
        new KeyName[] {
          PartitionKeyNames.PARTITION,
          ColumnFamilyMetricsKeyName.COLUMN_FAMILY,
          ColumnFamilyMetricsKeyName.OPERATION
        };
    private static final Duration[] BUCKETS =
        MicrometerUtil.exponentialBucketDuration(10, 2, 15, ChronoUnit.MICROS);

    @Override
    public String getName() {
      return "zeebe.rocksdb.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Latency of RocksDB operations per column family";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEYS;
    }
  };

  @SuppressWarnings("NullableProblems")
  public enum ColumnFamilyMetricsKeyName implements KeyName {
    /** The name of the column family */
    COLUMN_FAMILY {
      @Override
      public String asString() {
        return "columnFamily";
      }
    },
    /**
     * The type of operation with value {@link
     * io.camunda.zeebe.db.ColumnFamilyMetricsDoc.OperationType}
     */
    OPERATION {
      @Override
      public String asString() {
        return "operation";
      }
    }
  }

  /**
   * Type of the operation performend on RocksDB.
   *
   * <p>When adding a new case, remember to add a timer to {@link
   * io.camunda.zeebe.db.impl.FineGrainedColumnFamilyMetrics}
   */
  public enum OperationType {
    GET("get"),
    PUT("put"),
    DELETE("delete"),
    ITERATE("iterate");
    private final String name;

    OperationType(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}
