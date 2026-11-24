/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

public enum WorkerMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Total number of message publications attempted by the worker before completing a job, by their
   * {@code outcome}: {@code success} or {@code failure}. Failures also carry the {@code error}
   * type, e.g. {@code http_503} or {@code TimeoutException}; successes use {@code none}. Only
   * counted when the worker is configured to send a message.
   */
  MESSAGE_PUBLICATIONS {
    @Override
    public KeyName[] getKeyNames() {
      return OUTCOME_KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Total number of message publications by the worker, by outcome and error type.";
    }

    @Override
    public String getName() {
      return "worker.message.publications";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Total number of answered job completion requests by their {@code outcome}: {@code success} or
   * {@code failure}. Failures also carry the {@code error} type, e.g. {@code http_503} or {@code
   * grpc_unavailable}; successes use {@code none}. Counted when the answer arrives, so requests
   * without an answer, or dropped because the response queue is full, are not included.
   */
  JOB_COMPLETIONS {
    @Override
    public KeyName[] getKeyNames() {
      return OUTCOME_KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Total number of answered job completion requests by outcome and error type.";
    }

    @Override
    public String getName() {
      return "worker.job.completions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Total number of job completions whose result is no longer tracked because the response queue
   * was full. Their outcome is missing from {@code worker.job.completions}, so a growing value
   * means that counter understates the real number of completions.
   */
  JOB_COMPLETION_TRACKING_DROPPED {
    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[0];
    }

    @Override
    public String getDescription() {
      return "Total number of job completions dropped from result tracking because the queue was full.";
    }

    @Override
    public String getName() {
      return "worker.job.completion.tracking.dropped";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Number of job completion requests queued and not yet awaited. Close to the queue capacity means
   * the worker cannot keep up with checking the answers.
   */
  JOB_COMPLETIONS_PENDING {
    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[0];
    }

    @Override
    public String getDescription() {
      return "Number of job completion requests queued and not yet awaited.";
    }

    @Override
    public String getName() {
      return "worker.job.completions.pending";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };

  private static final KeyName[] OUTCOME_KEY_NAMES =
      new KeyName[] {ResultMetricKeyNames.OUTCOME, ResultMetricKeyNames.ERROR};
}
