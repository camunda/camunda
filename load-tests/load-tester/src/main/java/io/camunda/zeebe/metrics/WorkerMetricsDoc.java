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
   * How long a job waited between the broker activating it and a handler thread picking it up: the
   * delivery path, covering broker, gateway, the client's job stream or activation response, and
   * the client's own handler queue. It is the only segment of the job round-trip that none of the
   * other timers here cover, which makes it the one to read when the worker has idle threads and
   * still falls short of the offered job rate.
   *
   * <p>Derived from the job's deadline, which the broker sets to the activation instant plus the
   * configured job timeout, so it measures against the broker's clock rather than the worker's: a
   * clock offset between the two shifts every sample by the same amount. Compare runs on the same
   * pods and read the difference, not the absolute value. Samples that skew negative are dropped by
   * the timer.
   *
   * <p>The bucket at 100ms is the default client poll interval: polled jobs spread evenly below it,
   * whereas streamed jobs are pushed on creation and should sit far lower.
   */
  INTAKE_DELAY {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(150),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2)
    };

    @Override
    public String getDescription() {
      return "The delay between the broker activating a job and a handler thread picking it up.";
    }

    @Override
    public String getName() {
      return "worker.intake.delay";
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
   * The round-trip duration of the job completion dispatched at the end of job handling, from
   * sending the command until the response arrives. The completion is sent without being awaited,
   * so its latency never shows up in {@link #HANDLE_DURATION} — yet it delays the broker observing
   * the job as done, and with it the creation of the next job in the process instance. That makes
   * it the signal that explains throughput loss which the per-thread occupancy metrics cannot see.
   *
   * <p>Buckets are dense around one second because that is the fixed retry interval Apache HC5
   * applies to a retried REST request (HTTP 429/503 or a stale connection), so a single retry is
   * distinguishable from a genuinely slow broker.
   */
  COMPLETE_DURATION {
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
      Duration.ofMillis(1250),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10)
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The round-trip duration of the dispatched job completion, tagged by outcome.";
    }

    @Override
    public String getName() {
      return "worker.complete.duration";
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
