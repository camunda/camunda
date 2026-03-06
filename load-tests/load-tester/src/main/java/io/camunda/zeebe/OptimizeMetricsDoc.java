/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

public enum OptimizeMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * Response time for evaluating the management dashboard. This measures the time taken to fetch
   * the dashboard configuration and metadata.
   */
  DASHBOARD_RESPONSE_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
    };

    @Override
    public String getDescription() {
      return "Response time for evaluating the management dashboard";
    }

    @Override
    public String getName() {
      return "optimize.dashboard.response.time";
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
   * Maximum (slowest) report response time per evaluation cycle. This represents the bottleneck
   * when reports are loaded in parallel in the UI.
   */
  REPORT_MAX_RESPONSE_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
    };

    @Override
    public String getDescription() {
      return "Maximum (slowest) report response time per evaluation cycle";
    }

    @Override
    public String getName() {
      return "optimize.report.max.response.time";
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
   * Total homepage load time (user-perceived). This is dashboard load time plus the maximum report
   * time, since reports load in parallel in the UI.
   */
  HOMEPAGE_LOAD_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
      Duration.ofSeconds(90),
    };

    @Override
    public String getDescription() {
      return "Total homepage load time (dashboard + max report time)";
    }

    @Override
    public String getName() {
      return "optimize.homepage.load.time";
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

  /** Count of successful dashboard evaluations. */
  DASHBOARD_SUCCESS {
    @Override
    public String getDescription() {
      return "Successful dashboard evaluations";
    }

    @Override
    public String getName() {
      return "optimize.dashboard.success";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /** Count of failed dashboard evaluations. */
  DASHBOARD_ERROR {
    @Override
    public String getDescription() {
      return "Failed dashboard evaluations";
    }

    @Override
    public String getName() {
      return "optimize.dashboard.error";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Response time for evaluating the instant benchmark dashboard. This measures the time taken to
   * fetch the benchmark dashboard configuration.
   */
  BENCHMARK_DASHBOARD_RESPONSE_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
    };

    @Override
    public String getDescription() {
      return "Response time for instant benchmark dashboard evaluation";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.dashboard.response.time";
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
   * Maximum (slowest) report evaluation time per benchmark cycle. This represents the time to
   * evaluate reports in the benchmark flow.
   */
  BENCHMARK_REPORT_MAX_EVALUATION_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
    };

    @Override
    public String getDescription() {
      return "Maximum (slowest) report evaluation time per benchmark cycle";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.report.max.evaluation.time";
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
   * Maximum (slowest) detailed evaluation time per benchmark cycle. This represents the time to
   * perform detailed evaluations on reports.
   */
  BENCHMARK_DETAILED_MAX_EVALUATION_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(1500),
      Duration.ofSeconds(2),
      Duration.ofSeconds(3),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
    };

    @Override
    public String getDescription() {
      return "Maximum (slowest) detailed evaluation time per benchmark cycle";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.detailed.max.evaluation.time";
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
   * Total load time for the instant benchmark flow. This includes dashboard load, all report
   * evaluations, and detailed evaluations.
   */
  BENCHMARK_TOTAL_LOAD_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofSeconds(1),
      Duration.ofSeconds(2),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(60),
      Duration.ofSeconds(90),
      Duration.ofSeconds(120),
      Duration.ofSeconds(180),
    };

    @Override
    public String getDescription() {
      return "Total load time for instant benchmark flow";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.total.load.time";
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

  /** Count of successful benchmark dashboard evaluations. */
  BENCHMARK_DASHBOARD_SUCCESS {
    @Override
    public String getDescription() {
      return "Successful benchmark dashboard evaluations";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.dashboard.success";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /** Count of failed benchmark dashboard evaluations. */
  BENCHMARK_DASHBOARD_ERROR {
    @Override
    public String getDescription() {
      return "Failed benchmark dashboard evaluations";
    }

    @Override
    public String getName() {
      return "optimize.benchmark.dashboard.error";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  };
}
