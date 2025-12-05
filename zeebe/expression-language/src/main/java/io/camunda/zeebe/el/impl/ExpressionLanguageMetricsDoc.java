/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import java.time.Duration;

/** Documentation for all FEEL expression language related metrics */
@SuppressWarnings("NullableProblems")
public enum ExpressionLanguageMetricsDoc implements MeterDocumentation {

  /** Time spent parsing a FEEL expression (in seconds) */
  EXPRESSION_PARSING_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(100_000), // 100 micros
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1)
    };

    @Override
    public String getName() {
      return "zeebe.feel.expression.parsing.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    public String getDescription() {
      return "Time spent parsing a FEEL expression (in seconds)";
    }

    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /** Time spent evaluating a FEEL expression (in seconds) */
  EXPRESSION_EVALUATION_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(100_000), // 100 micros
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1)
    };

    @Override
    public String getName() {
      return "zeebe.feel.expression.evaluation.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    public String getDescription() {
      return "Time spent evaluating a FEEL expression (in seconds)";
    }

    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /** Total number of FEEL expression evaluations */
  EXPRESSION_EVALUATIONS_TOTAL {
    @Override
    public String getName() {
      return "zeebe.feel.expression.evaluations.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    public String getDescription() {
      return "Total number of FEEL expression evaluations";
    }
  };

  public abstract String getDescription();

  public Duration[] getTimerSLOs() {
    return new Duration[0];
  }
}
