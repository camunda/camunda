/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public enum AuthorizationMetricsDoc implements ExtendedMeterDocumentation {

  /** Latency of each authorization check in AuthorizationCheckBehavior, including cache hits. */
  CHECK_LATENCY {
    private static final Duration[] TIMER_SLOS = {
      Duration.of(100, ChronoUnit.MICROS),
      Duration.of(500, ChronoUnit.MICROS),
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(500),
    };

    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          AuthorizationKeyNames.RESOURCE_TYPE,
          AuthorizationKeyNames.PERMISSION_TYPE,
          AuthorizationKeyNames.OUTCOME,
        };

    @Override
    public String getName() {
      return "zeebe.authorization.check.latency";
    }

    @Override
    public String getDescription() {
      return "Latency of each authorization check in AuthorizationCheckBehavior, including cache hits";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return TIMER_SLOS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  };

  /** Tags used by authorization metrics. */
  public enum AuthorizationKeyNames implements KeyName {

    /** The resource type being authorized (e.g. PROCESS_DEFINITION, DECISION_DEFINITION). */
    RESOURCE_TYPE {
      @Override
      public String asString() {
        return "resourceType";
      }
    },

    /** The permission type being checked (e.g. READ, CREATE, UPDATE, DELETE). */
    PERMISSION_TYPE {
      @Override
      public String asString() {
        return "permissionType";
      }
    },

    /** The outcome of the authorization check. See {@link AuthorizationOutcome}. */
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    }
  }

  /** Possible outcomes of an authorization check recorded in metrics. */
  public enum AuthorizationOutcome {
    AUTHORIZED("authorized"),
    DENIED("denied");

    private final String label;

    AuthorizationOutcome(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }
}
