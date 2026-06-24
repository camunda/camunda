/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public enum BackupManagerMetricsDoc implements ExtendedMeterDocumentation {
  /** Total number of backup operations */
  BACKUP_OPERATIONS_TOTAL {
    @Override
    public String getDescription() {
      return "Total number of backup operations";
    }

    @Override
    public String getName() {
      return "zeebe.backup.operations.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION, MetricKeyName.OPERATION, MetricKeyName.RESULT};
    }
  },

  /** Number of backup operations that are in progress */
  BACKUP_OPERATIONS_IN_PROGRESS {
    @Override
    public String getDescription() {
      return "Number of backup operations that are in progress";
    }

    @Override
    public String getName() {
      return "zeebe.backup.operations.in.progress";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION, MetricKeyName.OPERATION};
    }
  },

  /** Latency of backup operations */
  BACKUP_OPERATIONS_LATENCY {
    @Override
    public String getDescription() {
      return "Latency of backup operations";
    }

    @Override
    public String getName() {
      return "zeebe.backup.operations.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "seconds";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION, MetricKeyName.OPERATION};
    }

    @Override
    public Duration[] getTimerSLOs() {
      return Stream.of(10, 100, 1000, 10_000, 60_000, 5 * 60_000)
          .map(s -> Duration.ofMillis(s.longValue()))
          .toArray(Duration[]::new);
    }
  };

  public enum OperationType {
    // Operation values
    TAKE("take"),
    STATUS("status"),
    LIST("list"),
    DELETE("delete");

    private final String value;

    OperationType(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public enum OperationResult {
    // Result values
    FAILED("failed"),
    COMPLETED("completed");

    private final String value;

    OperationResult(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  @SuppressWarnings("NullableProblems")
  public enum MetricKeyName implements KeyName {
    /** The id of the partition */
    PARTITION("partition"),
    /** The type of backup operation */
    OPERATION("operation"),
    /** The result of the operation (completed/failed) */
    RESULT("result");

    private final String key;

    MetricKeyName(final String key) {
      this.key = key;
    }

    @Override
    public String asString() {
      return key;
    }
  }
}
