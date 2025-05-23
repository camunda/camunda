/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

public enum AuthorizationCheckMetricsDoc implements ExtendedMeterDocumentation {
  AUTHORIZATION_CHECK_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(50),
      Duration.ofNanos(75),
      Duration.ofNanos(100),
      Duration.ofNanos(250),
      Duration.ofNanos(500),
      Duration.ofNanos(750),
      Duration.ofNanos(1000),
      Duration.ofNanos(1500),
      Duration.ofNanos(2000),
      Duration.ofNanos(4000),
      Duration.ofNanos(6000),
      Duration.ofNanos(10000)
    };

    @Override
    public String getDescription() {
      return "The time took to do the authorization checks";
    }

    @Override
    public String getName() {
      return "zeebe.authorization.check.time";
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

  GET_AUTHORIZED_RESORCE_IDENTIFIER_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofNanos(50),
      Duration.ofNanos(75),
      Duration.ofNanos(100),
      Duration.ofNanos(250),
      Duration.ofNanos(500),
      Duration.ofNanos(750),
      Duration.ofNanos(1000),
      Duration.ofNanos(1500),
      Duration.ofNanos(2000),
      Duration.ofNanos(4000),
      Duration.ofNanos(6000),
      Duration.ofNanos(10000)
    };

    @Override
    public String getDescription() {
      return "The time took to retrieve the resource ids. Used when activating jobs";
    }

    @Override
    public String getName() {
      return "zeebe.authorization.get.identifiers.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  }
}
