/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

public enum WorkerMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Latency of the job completion commands sent by the worker, tagged with the {@code outcome} and,
   * for failures, the {@code error} type. Counted when the answer arrives, so completions without
   * an answer are not included.
   */
  JOB_COMPLETION {
    @Override
    public KeyName[] getKeyNames() {
      return OUTCOME_AND_ERROR;
    }

    @Override
    public String getDescription() {
      return "Latency of job completion commands sent by the worker, by outcome and error type.";
    }

    @Override
    public String getName() {
      return "worker.job.completion";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /**
   * Latency of the messages published by the worker before it completes a job, tagged with the
   * {@code outcome} and, for failures, the {@code error} type. A failed publish leaves the job to
   * time out, so this is the only place where such failures are visible.
   */
  MESSAGE_PUBLISH {
    @Override
    public KeyName[] getKeyNames() {
      return OUTCOME_AND_ERROR;
    }

    @Override
    public String getDescription() {
      return "Latency of messages published by the worker, by outcome and error type.";
    }

    @Override
    public String getName() {
      return "worker.message.publish";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /**
   * Number of job completions the worker stopped tracking because the response-check queue was
   * full. Their result is lost, so a growing value means completion failures may be hidden.
   */
  COMPLETION_TRACKING_DROPPED {
    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[0];
    }

    @Override
    public String getDescription() {
      return "Number of job completions whose response is no longer tracked because the queue is full.";
    }

    @Override
    public String getName() {
      return "worker.job.completion.tracking.dropped";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  };

  private static final KeyName[] OUTCOME_AND_ERROR =
      new KeyName[] {StarterMetricKeyNames.OUTCOME, StarterMetricKeyNames.ERROR};

  private static final Duration[] BUCKETS = {
    Duration.ofMillis(10),
    Duration.ofMillis(25),
    Duration.ofMillis(50),
    Duration.ofMillis(100),
    Duration.ofMillis(250),
    Duration.ofMillis(500),
    Duration.ofMillis(1000),
    Duration.ofMillis(2500),
    Duration.ofMillis(5000),
    Duration.ofMillis(10000),
    Duration.ofMillis(15000)
  };
}
