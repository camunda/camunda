/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.camunda.zeebe.util.Environment;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppenderFlowControl {
  private static final Logger LOG = LoggerFactory.getLogger(AppenderFlowControl.class);
  private static final Map<String, BackpressureCfg> ALGORITHM_CFG =
      Map.of("vegas", new BackpressureCfgVegas(), "gradient2", new BackpressureCfgGradient2());

  private final AppendErrorHandler errorHandler;
  private final Limiter<Void> limiter;
  private final AppenderMetrics metrics;

  public AppenderFlowControl(final AppendErrorHandler errorHandler, final int partitionId) {
    this.errorHandler = errorHandler;
    metrics = new AppenderMetrics(partitionId);
    limiter = configureLimiter();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An {@link AppendInFlight} if append was accepted, null otherwise.
   */
  public AppendInFlight tryAcquire() {
    return limiter
        .acquire(null)
        .map(limiterListener -> new AppendInFlight(errorHandler, limiterListener, metrics))
        .orElseGet(
            () -> {
              metrics.increaseDeferredAppends();
              LOG.trace("Skipping append due to backpressure");
              return null;
            });
  }

  private Limiter<Void> configureLimiter() {
    final var env = new Environment();
    final boolean isBackpressureEnabled =
        env.getBool(BackpressureConstants.ENV_BP_APPENDER).orElse(true);

    if (!isBackpressureEnabled) {
      return new NoopLimiter(metrics);
    }

    final var algorithmName =
        env.get(BackpressureConstants.ENV_BP_APPENDER_ALGORITHM).orElse("vegas").toLowerCase();
    final var algorithmCfg = ALGORITHM_CFG.getOrDefault(algorithmName, new BackpressureCfgVegas());
    algorithmCfg.applyEnvironment(env);

    final var abstractLimit = algorithmCfg.get();
    final var windowedLimiter =
        env.getBool(BackpressureConstants.ENV_BP_APPENDER_WINDOWED).orElse(false);

    LOG.debug(
        "Configured log appender back pressure as {}. Window limiting is {}",
        algorithmCfg,
        windowedLimiter ? "enabled" : "disabled");
    return AppendLimiter.builder()
        .limit(windowedLimiter ? WindowedLimit.newBuilder().build(abstractLimit) : abstractLimit)
        .metrics(metrics)
        .build();
  }
}
