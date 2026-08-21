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

/** Metrics emitted by the zeebe-secrets benchmark driver. */
public enum ZeebeSecretsDriverMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * The end-to-end latency of a single secrets API request, from just before the request is issued
   * to when its response (or error) completes. Tagged by {@code endpoint} (resolve or list) and
   * {@code outcome} (success or error) so a dashboard can read p50/p95/p99 per endpoint and split
   * tail latency by success and failure. This is the primary benchmark signal for the API.
   */
  REQUEST_LATENCY {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          ZeebeSecretsDriverMetricKeyNames.ENDPOINT, ZeebeSecretsDriverMetricKeyNames.OUTCOME
        };

    private static final Duration[] BUCKETS = {
      Duration.ofMillis(1),
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
      return "The end-to-end latency of a single secrets API request, tagged by endpoint and outcome.";
    }

    @Override
    public String getName() {
      return "zeebe.secrets.request.latency";
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
   * Total number of secrets API requests submitted by the driver, tagged by {@code endpoint}.
   * Incremented before the request is issued, so it counts attempted submissions (a measure of
   * offered load), and lets a finite run compute throughput as the counter delta over its duration.
   */
  REQUESTS_SUBMITTED {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {ZeebeSecretsDriverMetricKeyNames.ENDPOINT};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Total number of secrets API requests submitted by the driver.";
    }

    @Override
    public String getName() {
      return "zeebe.secrets.requests.submitted";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * An "info"-style gauge constantly set to 1 whose tags carry the static driver configuration —
   * the endpoint mix and the batch shape — so dashboards can surface how a run was configured
   * alongside its latency and throughput.
   */
  DRIVER_INFO {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          ZeebeSecretsDriverMetricKeyNames.RESOLVE_RATIO,
          ZeebeSecretsDriverMetricKeyNames.BATCH_SIZE,
          ZeebeSecretsDriverMetricKeyNames.DUPLICATE_RATIO,
          ZeebeSecretsDriverMetricKeyNames.NB_THREADS
        };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The configuration of the zeebe-secrets benchmark driver.";
    }

    @Override
    public String getName() {
      return "zeebe.secrets.driver.info";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Set to 1 once the driver has finished its request loop (its duration-limit elapsed or it was
   * otherwise stopped), 0 while it is actively issuing requests. Lets an external watcher detect
   * completion without relying on pod phase, since the WebFlux server keeps the JVM alive after the
   * CommandLineRunner returns.
   */
  RUN_FINISHED {
    @Override
    public String getDescription() {
      return "1 once the zeebe-secrets driver has finished its request loop, 0 otherwise.";
    }

    @Override
    public String getName() {
      return "zeebe.secrets.run.finished";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };

  public enum ZeebeSecretsDriverMetricKeyNames implements KeyName {

    /** The endpoint the request targeted: {@code resolve} or {@code list}. */
    ENDPOINT {
      @Override
      public String asString() {
        return "endpoint";
      }
    },

    /** The outcome of the request: {@code success} or {@code error}. */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    },

    /** The configured fraction of requests that target the resolve endpoint. */
    RESOLVE_RATIO {
      @Override
      public String asString() {
        return "resolve_ratio";
      }
    },

    /** The configured number of references per resolve batch. */
    BATCH_SIZE {
      @Override
      public String asString() {
        return "batch_size";
      }
    },

    /** The configured fraction of each resolve batch that is duplicated references. */
    DUPLICATE_RATIO {
      @Override
      public String asString() {
        return "duplicate_ratio";
      }
    },

    /** The number of threads configured in the driver. */
    NB_THREADS {
      @Override
      public String asString() {
        return "nb_threads";
      }
    },
  }
}
