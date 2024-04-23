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
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Environment;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);
  private static final Map<String, BackpressureCfg> ALGORITHM_CFG =
      Map.of("vegas", new BackpressureCfgVegas(), "gradient2", new BackpressureCfgGradient2());

  private final AppendErrorHandler errorHandler;
  private final Limiter<Void> limiter;
  private final LogStreamMetrics metrics;

  public FlowControl(final AppendErrorHandler errorHandler, final LogStreamMetrics metrics) {
    this.errorHandler = errorHandler;
    this.metrics = metrics;
    limiter = configureLimiter();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightAppend} if append was accepted, an empty
   *     Optional otherwise.
   */
  public Either<Rejection, InFlightAppend> tryAcquire() {
    final var appendLimitListener = limiter.acquire(null).orElse(null);
    if (appendLimitListener == null) {
      metrics.increaseDeferredAppends();
      LOG.trace("Skipping append due to backpressure");
      return Either.left(new AppendLimitExhausted());
    }

    return Either.right(new InFlightAppend(errorHandler, appendLimitListener, metrics));
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

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}
  }
}
