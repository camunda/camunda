/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection.AppendLimitExhausted;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowControl {
  private static final Logger LOG = LoggerFactory.getLogger(FlowControl.class);

  private final Limiter<Void> appendLimiter;
  private final LogStreamMetrics metrics;

  public FlowControl(final LogStreamMetrics metrics) {
    this(metrics, VegasLimit.newDefault());
  }

  public FlowControl(final LogStreamMetrics metrics, final Limit appendLimit) {
    this.metrics = metrics;
    appendLimiter =
        appendLimit != null
            ? AppendLimiter.builder().limit(appendLimit).metrics(metrics).build()
            : new NoopLimiter();
  }

  /**
   * Tries to acquire a free in-flight spot, applying backpressure as needed.
   *
   * @return An Optional containing a {@link InFlightAppend} if append was accepted, an empty
   *     Optional otherwise.
   */
  public Either<Rejection, InFlightAppend> tryAcquire() {
    final var appendListener = appendLimiter.acquire(null).orElse(null);
    if (appendListener == null) {
      metrics.increaseDeferredAppends();
      LOG.trace("Skipping append due to backpressure");
      return Either.left(new AppendLimitExhausted());
    }

    return Either.right(new InFlightAppend(appendListener, metrics));
  }

  public sealed interface Rejection {
    record AppendLimitExhausted() implements Rejection {}
  }
}
