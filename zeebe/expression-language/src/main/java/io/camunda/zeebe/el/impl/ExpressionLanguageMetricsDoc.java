/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/** Documentation for all FEEL expression language related metrics */
@SuppressWarnings("NullableProblems")
public enum ExpressionLanguageMetricsDoc implements ExtendedMeterDocumentation {

  /** Time spent parsing a FEEL expression (in seconds) */
  EXPRESSION_PARSING_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(1),
      Duration.ofMillis(2),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(20),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(200),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
    };

    @Override
    public String getName() {
      return "zeebe.feel.expression.parsing.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spent parsing a FEEL expression (in seconds)";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return OutcomeKeyNames.values();
    }
  },

  /** Time spent evaluating a FEEL expression (in seconds) */
  EXPRESSION_EVALUATION_DURATION {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofSeconds(2),
      Duration.ofSeconds(4)
    };

    @Override
    public String getName() {
      return "zeebe.feel.expression.evaluation.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spent evaluating a FEEL expression (in seconds)";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return OutcomeKeyNames.values();
    }
  };

  /** Outcome values for expression parsing and evaluation */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /** Key names for expression metrics */
  public enum OutcomeKeyNames implements KeyName {
    /** The outcome of the operation (success or failure) */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    }
  }
}
