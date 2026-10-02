/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import java.util.function.LongConsumer;

/**
 * Throttles jobs available notifications of a single job type: a notification is handled right away
 * unless the previous one was handled less than a window ago, in which case a single trailing
 * wake-up is requested for when the window ends.
 *
 * <p>Compares timestamps instead of relying on a timer chain, so a backwards jump of the actor
 * clock cannot leave a job type throttled: a negative elapsed time is treated as an elapsed window.
 *
 * <p>Not thread-safe; must only be accessed from the handler actor.
 */
final class NotificationThrottle {

  // far enough in the past that the first notification is never throttled, without risking overflow
  private static final long NEVER_HANDLED = Long.MIN_VALUE / 2;

  private long lastHandledMillis = NEVER_HANDLED;
  private boolean flushPending;

  /**
   * Handles the notification via {@code handle} unless it is throttled. If throttled and no
   * trailing wake-up is pending yet, calls {@code scheduleFlush} with the delay in milliseconds
   * after which {@link #shouldFlush(long)} must be consulted.
   */
  void onNotification(
      final long nowMillis,
      final long windowMillis,
      final Runnable handle,
      final LongConsumer scheduleFlush) {
    final long elapsed = nowMillis - lastHandledMillis;
    if (windowMillis == 0 || elapsed >= windowMillis || elapsed < 0) {
      markHandled(nowMillis);
      handle.run();
    } else if (!flushPending) {
      flushPending = true;
      scheduleFlush.accept(windowMillis - elapsed);
    }
  }

  /** Returns true if a trailing wake-up is pending and must be handled now. */
  boolean shouldFlush(final long nowMillis) {
    if (!flushPending) {
      return false;
    }
    markHandled(nowMillis);
    return true;
  }

  private void markHandled(final long nowMillis) {
    lastHandledMillis = nowMillis;
    flushPending = false;
  }
}
