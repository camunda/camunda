/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.AuthResultValues;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationMetricsDoc.LatencyKeyNames;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class AuthenticationMetrics {
  private final MeterRegistry meterRegistry;
  private final Timer successLatencyTimer;
  private final Timer failureLatencyTimer;

  @VisibleForTesting
  AuthenticationMetrics() {
    this(new SimpleMeterRegistry(), AuthenticationMethod.BASIC);
  }

  public AuthenticationMetrics(
      final MeterRegistry meterRegistry, final AuthenticationMethod method) {
    this.meterRegistry = meterRegistry;
    successLatencyTimer =
        MicrometerUtil.buildTimer(AuthenticationMetricsDoc.LATENCY)
            .tag(LatencyKeyNames.AUTH_METHOD.asString(), method.name())
            .tag(LatencyKeyNames.AUTH_RESULT.asString(), AuthResultValues.SUCCESS.getValue())
            .register(meterRegistry);
    failureLatencyTimer =
        MicrometerUtil.buildTimer(AuthenticationMetricsDoc.LATENCY)
            .tag(LatencyKeyNames.AUTH_METHOD.asString(), method.name())
            .tag(LatencyKeyNames.AUTH_RESULT.asString(), AuthResultValues.FAILURE.getValue())
            .register(meterRegistry);
  }

  public Timer.Sample startLatencySample() {
    return Timer.start(meterRegistry);
  }

  public void recordSuccessLatency(final Timer.Sample sample) {
    sample.stop(successLatencyTimer);
  }

  public void recordFailureLatency(final Timer.Sample sample) {
    sample.stop(failureLatencyTimer);
  }
}
