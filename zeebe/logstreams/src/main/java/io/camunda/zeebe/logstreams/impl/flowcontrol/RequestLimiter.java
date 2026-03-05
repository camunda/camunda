/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Optional;

public final class RequestLimiter extends AbstractLimiter<Intent> {

  private final LogStreamMetrics metrics;

  private RequestLimiter(final CommandRateLimiterBuilder builder, final LogStreamMetrics metrics) {
    super(builder);
    this.metrics = metrics;
    metrics.setInflightRequests(0);
    metrics.setRequestLimit(getLimit());
  }

  @Override
  public Optional<Listener> acquire(final Intent intent) {
    if (getInflight() >= getLimit() && !WhiteListedCommands.isWhitelisted(intent)) {
      return createRejectedListener();
    }
    final Listener listener = createListener();
    return Optional.of(listener);
  }

  @Override
  protected void onNewLimit(final int newLimit) {
    super.onNewLimit(newLimit);
    metrics.setRequestLimit(newLimit);
  }

  public static CommandRateLimiterBuilder builder() {
    return new CommandRateLimiterBuilder();
  }

  public static class CommandRateLimiterBuilder
      extends AbstractLimiter.Builder<CommandRateLimiterBuilder> {

    @Override
    protected CommandRateLimiterBuilder self() {
      return this;
    }

    public RequestLimiter build(final LogStreamMetrics metrics) {
      return new RequestLimiter(this, metrics);
    }
  }
}
