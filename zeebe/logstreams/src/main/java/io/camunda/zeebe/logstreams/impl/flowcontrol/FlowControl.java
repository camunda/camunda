/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);

  private final Limiter<Void> limiter;
  private final LogStreamMetrics metrics;

  public FlowControl(final LogStreamMetrics metrics) {
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

    return Either.right(new InFlightAppend(appendLimitListener, metrics));
  }

  private Limiter<Void> configureLimiter() {
    final var algorithmCfg = new BackpressureCfgVegas();
    final var abstractLimit = algorithmCfg.get();
    LOG.debug(
        "Configured log appender back pressure as {}. Window limiting is disabled", algorithmCfg);
    return AppendLimiter.builder().limit(abstractLimit).metrics(metrics).build();
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}
  }
}
