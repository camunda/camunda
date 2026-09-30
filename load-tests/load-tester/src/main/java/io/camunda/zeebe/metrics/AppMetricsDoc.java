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
import java.util.stream.Stream;

/** Metrics shared across all app types (Starter and Worker). */
public enum AppMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * A gauge set to 1 when the client successfully connects to the gateway (i.e. after the first
   * successful topology request), and 0 otherwise. This metric is used by the verification workflow
   * to confirm that the client is connected, regardless of whether the client uses gRPC or REST.
   */
  CONNECTED {
    @Override
    public String getDescription() {
      return "Set to 1 when the client successfully connects to the gateway (topology received), 0 otherwise.";
    }

    @Override
    public String getName() {
      return "app.connected";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Counts completed client requests by outcome, as the client observed them. Unlike the
   * gateway-side request metrics, this includes failures that never reach a broker (timeouts,
   * connection errors), and works whether the client uses gRPC or REST.
   */
  REQUESTS {
    private static final KeyName[] KEY_NAMES = RequestKeyNames.values();

    @Override
    public String getDescription() {
      return "Number of completed client requests, by command and outcome.";
    }

    @Override
    public String getName() {
      return "app.requests";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  },

  /**
   * Time from sending a client request to its response, as the client observed it, by command and
   * outcome. It includes any retries the client makes on its own, such as an HTTP retry of a 503,
   * and failed requests, which the gateway-side request latency leaves out.
   */
  REQUEST_LATENCY {
    private static final KeyName[] KEY_NAMES = {RequestKeyNames.COMMAND, RequestKeyNames.STATUS};

    @Override
    public String getDescription() {
      return "Latency of completed client requests, by command and outcome.";
    }

    @Override
    public String getName() {
      return "app.request.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return LatencyBuckets.BUCKETS;
    }
  },

  /**
   * Time from the starter sending the create request to the worker receiving the instance's job,
   * the client-side counterpart of job activation. Only recorded for instances whose variables
   * carry the starter's creation timestamp; starter and worker clocks may differ by a few ms.
   */
  JOB_RECEIVED_DELAY {
    @Override
    public String getDescription() {
      return "Time from the create request to the worker receiving the instance's job.";
    }

    @Override
    public String getName() {
      return "app.job.received.delay";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return LatencyBuckets.BUCKETS;
    }
  },

  /**
   * Time from the worker receiving a job to its completion being acknowledged, the client-side
   * counterpart of job lifetime, by completion outcome.
   */
  JOB_LIFETIME {
    private static final KeyName[] KEY_NAMES = {RequestKeyNames.STATUS};

    @Override
    public String getDescription() {
      return "Time from the worker receiving a job to its completion being acknowledged.";
    }

    @Override
    public String getName() {
      return "app.job.lifetime";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return LatencyBuckets.BUCKETS;
    }
  };

  private static final class LatencyBuckets {
    private static final Duration[] BUCKETS =
        Stream.of(
                10, 25, 50, 75, 100, 150, 200, 300, 400, 500, 650, 800, 1000, 1250, 1500, 1750,
                2000, 2500, 3000, 4000, 5000, 6000, 7500, 10_000, 15_000, 20_000, 30_000, 45_000,
                60_000)
            .map(Duration::ofMillis)
            .toArray(Duration[]::new);
  }

  public enum RequestKeyNames implements KeyName {
    /** The command that was sent, e.g. {@code create_instance} */
    COMMAND {
      @Override
      public String asString() {
        return "command";
      }
    },

    /**
     * {@code ok} on success; otherwise the HTTP status code, the gRPC status code, or the exception
     * type when the request failed without a response
     */
    STATUS {
      @Override
      public String asString() {
        return "status";
      }
    },

    /** The title of the REST problem detail, if any, which tells apart e.g. kinds of 503 */
    REASON {
      @Override
      public String asString() {
        return "reason";
      }
    }
  }
}
