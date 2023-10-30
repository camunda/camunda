/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.camunda.zeebe.logstreams.impl.metrics.AppenderMetrics;
import java.util.Optional;

final class AppendLimiter extends AbstractLimiter<Void> {

  private final AppenderMetrics metrics;

  private AppendLimiter(final Builder<?> builder, final AppenderMetrics metrics) {
    super(builder);
    this.metrics = metrics;
    metrics.setInflightLimit(getLimit());
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
    metrics.setInflightLimit(newLimit);
  }

  static final class AppenderLimiterBuilder
      extends AbstractLimiter.Builder<AppenderLimiterBuilder> {
    private AppenderMetrics metrics;

    public AppenderLimiterBuilder metrics(final AppenderMetrics metrics) {
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
