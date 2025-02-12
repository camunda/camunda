/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum CheckpointMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Number of checkpoint records processed by stream processor. Processing can result in either
   * creating a new checkpoint or ignoring the record. This can be observed by filtering for label
   * 'result'.
   */
  CHECKPOINTS_RECORDS {
    @Override
    public String getName() {
      return "zeebe.checkpoint.records.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of checkpoint records processed by stream processor. Processing can result in either creating a new checkpoint or ignoring the record. This can be observed by filtering for label 'result'.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION, CheckpointMetricsKeyName.RESULT};
    }
  },
  /** Position of the last checkpoint */
  CHECKPOINT_POSITION {
    @Override
    public String getName() {
      return "zeebe.checkpoint.position";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Position of the last checkpoint ";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Id of the last checkpoint */
  CHECKPOINT_ID {
    @Override
    public String getName() {
      return "zeebe.checkpoint.id";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Id of the last checkpoint ";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  };

  public enum CheckpointMetricsKeyName implements KeyName {
    RESULT {
      @Override
      public String asString() {
        return "result";
      }
    }
  }

  public enum CheckpointMetricsResultValue {
    CREATED("created"),
    IGNORED("ignored");
    private final String value;

    CheckpointMetricsResultValue(final String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }
}
