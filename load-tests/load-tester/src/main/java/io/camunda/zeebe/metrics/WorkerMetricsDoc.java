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
import java.time.Duration;

/** Metrics recorded by the Worker while handling jobs. */
public enum WorkerMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * The duration of the synchronous message publication issued while handling a job, from sending
   * the command until the response arrives (or the wait times out). Isolates the latency of the
   * publish call from the rest of the job handling, so its contribution to thread occupancy — e.g.
   * authentication and authorization overhead on the gateway — can be attributed.
   */
  PUBLISH_DURATION {
    private static final KeyName[] KEY_NAMES = new KeyName[] {WorkerMetricKeyNames.OUTCOME};

    private static final Duration[] BUCKETS = {
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10)
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The duration of the message publication issued while handling a job, tagged by outcome.";
    }

    @Override
    public String getName() {
      return "worker.publish.duration";
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
   * The total time a worker thread is occupied by a single job, covering the message publication,
   * the configured completion delay and the dispatch of the complete command. With a fixed job
   * concurrency this bounds the achievable throughput, so it is the metric that explains throughput
   * loss caused by added per-job latency. Recorded on every path, including the one where the
   * message publication failed and the job is left to time out.
   */
  HANDLE_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(300),
      Duration.ofMillis(350),
      Duration.ofMillis(400),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(30)
    };

    @Override
    public String getDescription() {
      return "The total time a worker thread is occupied by a single job.";
    }

    @Override
    public String getName() {
      return "worker.handle.duration";
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
   * The number of dispatched job completions whose response has not been checked yet. A growing
   * value means completions are being sent faster than the broker answers them; once it reaches the
   * queue capacity, tracking is dropped for further completions.
   */
  COMPLETION_QUEUE_DEPTH {
    @Override
    public String getDescription() {
      return "The number of dispatched job completions whose response has not been checked yet.";
    }

    @Override
    public String getName() {
      return "worker.completion.queue.depth";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };

  public enum WorkerMetricKeyNames implements KeyName {

    /** The outcome of the timed operation */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    },
  }
}
