/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.utils;

import org.slf4j.Logger;

/**
 * Logger that emits at most one message per time interval. Not thread-safe — designed for
 * single-threaded callers like a Zeebe actor.
 */
public final class SampledLogger {

  private final Logger delegate;
  private final long intervalMs;
  private long lastLogTimeMs;

  public SampledLogger(final Logger delegate, final long intervalMs) {
    this.delegate = delegate;
    this.intervalMs = intervalMs;
  }

  public boolean isLoggable() {
    return System.currentTimeMillis() - lastLogTimeMs >= intervalMs;
  }

  public void info(final String msg, final Object... args) {
    final long now = System.currentTimeMillis();
    if (now - lastLogTimeMs >= intervalMs) {
      delegate.info(msg, args);
      lastLogTimeMs = now;
    }
  }

  public void warn(final String msg, final Object... args) {
    final long now = System.currentTimeMillis();
    if (now - lastLogTimeMs >= intervalMs) {
      delegate.warn(msg, args);
      lastLogTimeMs = now;
    }
  }
}
