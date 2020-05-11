/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.backpressure;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.FixedLimit;
import io.zeebe.gateway.impl.configuration.AIMDCfg;
import io.zeebe.gateway.impl.configuration.BackpressureCfg;
import io.zeebe.gateway.impl.configuration.BackpressureCfg.LimitAlgorithm;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class LimitFactory {
  public Limit ofConfig(final BackpressureCfg config) {
    final LimitAlgorithm algorithm = config.getAlgorithm();

    switch (algorithm) {
      case AIMD:
        return newAimdLimit(config);
      case FIXED:
        return newFixedLimit(config);
      default:
        throw new IllegalArgumentException(
            String.format(
                "Expected one of the following algorithms [%s], but got %s",
                Arrays.toString(BackpressureCfg.LimitAlgorithm.values()), algorithm));
    }
  }

  private FixedLimit newFixedLimit(final BackpressureCfg config) {
    return FixedLimit.of(config.getFixedLimit().getLimit());
  }

  private AIMDLimit newAimdLimit(final BackpressureCfg backpressureCfg) {
    final AIMDCfg config = backpressureCfg.getAimdCfg();
    final Duration requestTimeout = config.getRequestTimeout();
    return AIMDLimit.newBuilder()
        .initialLimit(config.getInitialLimit())
        .minLimit(config.getMinLimit())
        .maxLimit(config.getMaxLimit())
        .backoffRatio(config.getBackoffRatio())
        .timeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .build();
  }
}
