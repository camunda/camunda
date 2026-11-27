/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.EngineKeyNames;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import org.jspecify.annotations.NullMarked;

/**
 * {@link JobBacklogMetricsDoc} documents the job backlog and activation lag metrics, per job type.
 *
 * <p>Every replica reports these gauges, not only the leader, so aggregate them with {@code max by
 * (partition, type)} before alerting.
 */
@NullMarked
@SuppressWarnings("NullableProblems")
public enum JobBacklogMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Number of jobs currently in an activatable index, i.e. jobs a worker can activate right now.
   * Jobs parked for secret resolution, in backoff, or suspended are not counted.
   */
  ACTIVATABLE_JOBS {
    @Override
    public String getName() {
      return "zeebe.job.activatable.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Number of jobs currently activatable per job type, i.e. waiting for a worker to"
          + " activate them. A job type without a backlog may have no series at all, for example"
          + " after a restart; treat an absent series as 0.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return JOB_TYPE_KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Activation lag per job type. The alert is documented in {@link #getDescription()}. */
  ACTIVATION_LAG {
    @Override
    public String getName() {
      return "zeebe.job.activation.lag";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getBaseUnit() {
      return "seconds";
    }

    @Override
    public String getDescription() {
      return "Seconds since the last activation or push of a job of this type, counted while jobs"
          + " of this type are activatable, 0 otherwise. After a restart or leader change the lag"
          + " restarts from zero on that replica. Every replica reports it, so aggregate with max"
          + " by (partition, type). Alert example, with a for: 5m clause: max by (partition, type)"
          + " (zeebe_job_activation_lag_seconds) > 300 and max by (partition, type)"
          + " (zeebe_job_activatable_count) > 0. The alert cannot see job types beyond the 500"
          + " types a partition tracks; a type evicted from that bound is not reported until its"
          + " backlog changes again. A job type without a backlog may have no series at all, for"
          + " example after a restart; treat an absent series as 0.";
    }

    @Override
    public KeyName[] getKeyNames() {
      return JOB_TYPE_KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  private static final KeyName[] JOB_TYPE_KEY_NAMES = new KeyName[] {EngineKeyNames.JOB_TYPE};
}
