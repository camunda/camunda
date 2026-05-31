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

public enum OptimizeMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Latency of Optimize report and dashboard evaluations executed against the running Optimize
   * deployment under load. Tags identify which page (homepage or detailed instant), which
   * evaluation phase (dashboard fetch or per-report evaluate), and the report id ({@code n/a} for
   * dashboard fetches).
   */
  REPORT_EVALUATION_LATENCY {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          OptimizeMetricKeyNames.PAGE,
          OptimizeMetricKeyNames.PHASE,
          OptimizeMetricKeyNames.REPORT_ID
        };

    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Latency of Optimize report and dashboard evaluations executed against the running Optimize deployment under load.";
    }

    @Override
    public String getName() {
      return "optimize.report.evaluation.latency";
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
   * Count of Optimize report/dashboard evaluation requests, tagged by HTTP status code so success
   * (2xx) and failure rates per code are both observable.
   */
  REPORT_EVALUATION_REQUESTS {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          OptimizeMetricKeyNames.PAGE,
          OptimizeMetricKeyNames.PHASE,
          OptimizeMetricKeyNames.STATUS_CODE
        };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Count of Optimize report or dashboard evaluation requests, tagged by the HTTP status code of the response.";
    }

    @Override
    public String getName() {
      return "optimize.report.evaluation.requests";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  };

  public enum OptimizeMetricKeyNames implements KeyName {

    /** Which Optimize page is being evaluated: {@code homepage} or {@code detailed}. */
    PAGE {
      @Override
      public String asString() {
        return "page";
      }
    },

    /**
     * Evaluation phase within a page: {@code dashboard} (initial dashboard fetch) or {@code
     * report_evaluate} (per-tile report evaluate).
     */
    PHASE {
      @Override
      public String asString() {
        return "phase";
      }
    },

    /** The Optimize report id being evaluated, or {@code n/a} for dashboard fetches. */
    REPORT_ID {
      @Override
      public String asString() {
        return "report_id";
      }
    },

    /** HTTP status code of the Optimize API response. */
    STATUS_CODE {
      @Override
      public String asString() {
        return "status_code";
      }
    },
  }
}
