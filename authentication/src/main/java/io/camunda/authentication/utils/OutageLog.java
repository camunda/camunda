/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * Collapses a failure that recurs on every request into one WARN per outage, repeated at most once
 * per {@link #REPEAT_INTERVAL} while it lasts. Without this, a degraded dependency on a per-request
 * path floods the log at a rate set by incoming traffic, which buries the outage rather than
 * reporting it; without the repeat, a condition that never recovers would be reported once and then
 * be invisible for as long as it lasts.
 *
 * <p>Hold one instance per independently failing thing, so an outage of one does not suppress the
 * report of another.
 */
public final class OutageLog {

  private static final Duration REPEAT_INTERVAL = Duration.ofMinutes(5);

  private final Logger log;
  private final long repeatIntervalNanos;
  private final LongSupplier nanoTime;
  private final AtomicBoolean degraded = new AtomicBoolean();
  private final AtomicLong nextWarnAt = new AtomicLong();

  public OutageLog(final Logger log) {
    this(log, REPEAT_INTERVAL, System::nanoTime);
  }

  OutageLog(final Logger log, final Duration repeatInterval, final LongSupplier nanoTime) {
    this.log = log;
    repeatIntervalNanos = repeatInterval.toNanos();
    this.nanoTime = nanoTime;
  }

  /**
   * Logs at WARN for the failure that starts an outage and for the first failure in each subsequent
   * {@link #REPEAT_INTERVAL} it lasts, and at DEBUG for every failure in between, until {@link
   * #recovery} closes it.
   */
  public void failure(final String format, final Object... args) {
    if (degraded.compareAndSet(false, true)) {
      nextWarnAt.set(nanoTime.getAsLong() + repeatIntervalNanos);
      log.warn(format, args);
      return;
    }
    final var now = nanoTime.getAsLong();
    final var due = nextWarnAt.get();
    // Subtract rather than compare, so a nanoTime that wraps does not silence the outage. The CAS
    // makes exactly one of several concurrent failures win the repeat.
    if (now - due >= 0 && nextWarnAt.compareAndSet(due, now + repeatIntervalNanos)) {
      log.warn(format, args);
    } else {
      log.debug(format, args);
    }
  }

  /** Logs at INFO for the first success after an outage, and stays silent when none was open. */
  public void recovery(final String format, final Object... args) {
    if (degraded.compareAndSet(true, false)) {
      log.info(format, args);
    }
  }
}
