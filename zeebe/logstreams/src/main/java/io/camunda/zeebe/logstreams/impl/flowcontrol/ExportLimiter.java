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

final class ExportLimiter extends AbstractLimiter<Void> {
  private final LogStreamMetrics metrics;

  private ExportLimiter(final Builder<?> builder, final LogStreamMetrics metrics) {
    super(builder);
    this.metrics = metrics;
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
    metrics.setExportLimit(newLimit);
  }

  public static ExportLimiterBuilder builder() {
    return new ExportLimiterBuilder();
  }

  static final class ExportLimiterBuilder extends AbstractLimiter.Builder<ExportLimiterBuilder> {
    private LogStreamMetrics metrics;

    public ExportLimiterBuilder metrics(final LogStreamMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    public ExportLimiter build() {
      return new ExportLimiter(this, metrics);
    }

    @Override
    protected ExportLimiterBuilder self() {
      return this;
    }
  }
}
