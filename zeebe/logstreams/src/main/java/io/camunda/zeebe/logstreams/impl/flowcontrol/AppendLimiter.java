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
import java.util.Optional;

public final class AppendLimiter extends AbstractLimiter<Void> {

  private final LogStreamMetrics metrics;

  private AppendLimiter(final Builder<?> builder, final LogStreamMetrics metrics) {
    super(builder);
    this.metrics = metrics;
    metrics.setAppendLimit(getLimit());
  }

  public static AppenderLimiterBuilder builder() {
    return new AppenderLimiterBuilder();
  }

  @Override
  public Optional<Listener> acquire(final Void context) {
    if (getInflight() >= getLimit()) {
      return createRejectedListener();
    } else {
      return Optional.of(createListener());
    }
  }

  @Override
  protected void onNewLimit(final int newLimit) {
    super.onNewLimit(newLimit);
    metrics.setAppendLimit(newLimit);
  }

  public static final class AppenderLimiterBuilder
      extends AbstractLimiter.Builder<AppenderLimiterBuilder> {
    private LogStreamMetrics metrics;

    public AppenderLimiterBuilder metrics(final LogStreamMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public AppendLimiter build() {
      return new AppendLimiter(this, metrics);
    }

    @Override
    protected AppenderLimiterBuilder self() {
      return this;
    }
  }
}
