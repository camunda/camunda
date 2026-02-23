/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * A logger wrapper that throttles log messages on a per-message basis. Unlike a global throttled
 * logger, this implementation tracks the last logged time for each unique message pattern
 * separately, allowing different messages to be logged independently while still preventing log
 * flooding from repeated identical messages.
 *
 * <p>The throttling is based on the message template (without parameters), so different
 * parameterized instances of the same message template share the same throttle state.
 */
public class PerMessageThrottledLogger {

  private final Logger delegate;
  private final long throttleMillis;
  private final Supplier<Long> timeSupplier;
  private final Map<String, Long> lastLogTimeByMessage = new ConcurrentHashMap<>();

  /**
   * Creates a new per-message throttled logger.
   *
   * @param delegate the underlying logger to delegate to
   * @param throttleInterval the minimum interval between logging the same message
   */
  public PerMessageThrottledLogger(final Logger delegate, final Duration throttleInterval) {
    this(delegate, throttleInterval, System::currentTimeMillis);
  }

  /**
   * Package-private constructor for testing purposes.
   *
   * @param delegate the underlying logger to delegate to
   * @param throttleInterval the minimum interval between logging the same message
   * @param timeSupplier supplier for getting the current time in milliseconds
   */
  PerMessageThrottledLogger(
      final Logger delegate, final Duration throttleInterval, final Supplier<Long> timeSupplier) {
    this.delegate = delegate;
    throttleMillis = throttleInterval.toMillis();
    this.timeSupplier = timeSupplier;
  }

  /**
   * Logs a warning message if the throttle interval has passed since the last time this message was
   * logged.
   *
   * @param messageTemplate the message template (may contain {} placeholders)
   * @param args the arguments to format into the message
   */
  public void warn(final String messageTemplate, final Object... args) {
    if (delegate.isWarnEnabled()) {
      logIfNotThrottled(messageTemplate, () -> delegate.warn(messageTemplate, args));
    }
  }

  /**
   * Logs an info message if the throttle interval has passed since the last time this message was
   * logged.
   *
   * @param messageTemplate the message template (may contain {} placeholders)
   * @param args the arguments to format into the message
   */
  public void info(final String messageTemplate, final Object... args) {
    if (delegate.isInfoEnabled()) {
      logIfNotThrottled(messageTemplate, () -> delegate.info(messageTemplate, args));
    }
  }

  /**
   * Logs an error message if the throttle interval has passed since the last time this message was
   * logged.
   *
   * @param messageTemplate the message template (may contain {} placeholders)
   * @param args the arguments to format into the message
   */
  public void error(final String messageTemplate, final Object... args) {
    if (delegate.isErrorEnabled()) {
      logIfNotThrottled(messageTemplate, () -> delegate.error(messageTemplate, args));
    }
  }

  /**
   * Logs a debug message if the throttle interval has passed since the last time this message was
   * logged.
   *
   * @param messageTemplate the message template (may contain {} placeholders)
   * @param args the arguments to format into the message
   */
  public void debug(final String messageTemplate, final Object... args) {
    if (delegate.isDebugEnabled()) {
      logIfNotThrottled(messageTemplate, () -> delegate.debug(messageTemplate, args));
    }
  }

  private void logIfNotThrottled(final String messageTemplate, final Runnable logAction) {
    // Ignore null messageTemplate
    if (messageTemplate == null) {
      return;
    }

    final long currentTime = timeSupplier.get();

    lastLogTimeByMessage.compute(
        messageTemplate,
        (key, lastTime) -> {
          if (lastTime == null || (currentTime - lastTime) >= throttleMillis) {
            logAction.run();
            return currentTime;
          }
          return lastTime;
        });
  }

  @VisibleForTesting
  void clearThrottleState() {
    lastLogTimeByMessage.clear();
  }

  @VisibleForTesting
  Map<String, Long> getLastLogTimeByMessage() {
    return Map.copyOf(lastLogTimeByMessage);
  }
}
