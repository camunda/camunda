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
public enum BackupMetadataSyncerMetricsDoc implements ExtendedMeterDocumentation {

  /** Total number of backup metadata sync attempts */
  METADATA_SYNC_TOTAL {
    @Override
    public String getDescription() {
      return "Total number of backup metadata sync attempts";
    }

    @Override
    public String getName() {
      return "zeebe.backup.metadata.sync.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION, MetricKeyName.RESULT};
    }
  },

  /** Duration of the backup metadata sync upload */
  METADATA_SYNC_DURATION {
    @Override
    public String getDescription() {
      return "Duration of the backup metadata sync";
    }

    @Override
    public String getName() {
      return "zeebe.backup.metadata.sync.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION};
    }

    @Override
    public Duration[] getTimerSLOs() {
      return Stream.of(5, 10, 100, 250, 500, 1000, 5_000, 10_000, 30_000, 60_000)
          .map(s -> Duration.ofMillis(s.longValue()))
          .toArray(Duration[]::new);
    }
  },

  /** Serialized size in bytes of the backup metadata */
  METADATA_SYNC_SERIALIZED_SIZE {
    @Override
    public String getDescription() {
      return "Serialized size of the backup metadata in bytes";
    }

    @Override
    public String getName() {
      return "zeebe.backup.metadata.sync.size";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {MetricKeyName.PARTITION};
    }
  };

  @SuppressWarnings("NullableProblems")
  public enum MetricKeyName implements KeyName {
    /** The id of the partition */
    PARTITION("partition"),
    /** The result of the sync attempt (completed/failed) */
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

  public enum SyncResult {
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    SyncResult(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
