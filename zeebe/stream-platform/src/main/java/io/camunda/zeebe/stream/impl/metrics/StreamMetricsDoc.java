/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/** Documentation for all stream platform related metrics */
@SuppressWarnings("NullableProblems")
public enum StreamMetricsDoc implements ExtendedMeterDocumentation {
  /** Time spent in batch processing (in seconds) */
  BATCH_PROCESSING_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(100_000), // 100 micros
      Duration.ofMillis(1),
      Duration.ofMillis(10),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofSeconds(2)
    };

    @Override
    public String getDescription() {
      return "Time spent in batch processing (in seconds)";
    }

    @Override
    public String getName() {
      return "zeebe.stream.processor.batch.processing.duration";
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

  /** Records the distribution of commands in a batch over time */
  BATCH_PROCESSING_COMMANDS {
    private static final double[] BUCKETS = {1, 2, 4, 8, 16, 32, 64, 128};

    @Override
    public String getDescription() {
      return "Records the distribution of commands in a batch over time";
    }

    @Override
    public String getName() {
      return "zeebe.stream.processor.batch.processing.commands";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }
  },

  /** Time spent in executing post commit tasks after batch processing (in seconds) */
  BATCH_PROCESSING_POST_COMMIT_TASKS {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(100_000), // 100 micros
      Duration.ofMillis(1),
      Duration.ofMillis(10),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofSeconds(2)
    };

    @Override
    public String getDescription() {
      return "Time spent in executing post commit tasks after batch processing (in seconds)";
    }

    @Override
    public String getName() {
      return "zeebe.stream.processor.batch.processing.post.commit.tasks";
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

  /** Number of times batch processing failed due to reaching batch limit and was retried */
  BATCH_PROCESSING_RETRIES {
    @Override
    public String getDescription() {
      return "Number of times batch processing failed due to reaching batch limit and was retried";
    }

    @Override
    public String getName() {
      return "zeebe.stream.processor.batch.processing.retry";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * The current phase of error handling the processor is in; see {@link
   * io.camunda.zeebe.stream.impl.ProcessingStateMachine.ErrorHandlingPhase} for possible values.
   */
  ERROR_HANDLING_PHASE {
    @Override
    public String getDescription() {
      return "The current phase of error handling the processor is in";
    }

    @Override
    public String getName() {
      return "zeebe.stream.processor.error.handling.phase";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return ErrorHandlingPhaseKeys.values();
    }
  },

  /** Number of events replayed by the stream processor */
  REPLAY_EVENTS_COUNT {
    @Override
    public String getDescription() {
      return "Number of events replayed by the stream processor";
    }

    @Override
    public String getName() {
      return "zeebe.replay.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /** The last source position the stream processor has replayed */
  LAST_SOURCE_POSITION {
    @Override
    public String getDescription() {
      return "The last source position the stream processor has replayed";
    }

    @Override
    public String getName() {
      return "zeebe.replay.last.source.position";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /** Time for replay a batch of events (in seconds) */
  REPLAY_DURATION {
    @Override
    public String getDescription() {
      return "Time for replay a batch of events (in seconds)";
    }

    @Override
    public String getName() {
      return "zeebe.replay.event.batch.replay.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }
  };

  public enum ErrorHandlingPhaseKeys implements KeyName {
    /**
     * Tag/key value specifying the current error handling phase in a human-readable way. This is
     * exactly the metric name for backwards compatibility with Prometheus' Enumeration metric.
     *
     * <p>Possible values are the names (as is) of the enum {@link
     * io.camunda.zeebe.stream.impl.ProcessingStateMachine.ErrorHandlingPhase}.
     */
    ERROR_HANDLING_PHASE {
      @Override
      public String asString() {
        return "zeebe_stream_processor_error_handling_phase";
      }
    }
  }
}
