/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.logging;

import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import net.jcip.annotations.GuardedBy;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Logger that logs only once every configured interval.
 *
 * <p>Example:
 *
 * <pre>
 * while(condition) {
 *     log.info("repeated log");
 * }
 * </pre>
 *
 * When ThrottledLogger is used, this is not logged in every loop. Instead, it will be logged once
 * in every configured interval.
 *
 * <p>The throttle is shared by all log levels and all threads that use the same instance: when
 * several threads log at the same time, exactly one of them wins the interval. Use a separate
 * instance for each use case, so that unrelated messages do not hide each other.
 *
 * <p>To show that something is still going on, a message that is logged after other calls were
 * suppressed gets a suffix such as {@code [3 suppressed since last log, ongoing for 1m 5s]}. The
 * count covers all calls to this instance that were suppressed since the last logged message, even
 * across a quiet period. A streak timer starts with the first logged message and is only reset when
 * more than two intervals pass without a logged message; once the streak lasted at least a second,
 * its duration is shown next to the count. A message logged without any suppressed call is left
 * unchanged.
 *
 * <p>Suppressed calls do not take a lock, so that callers which are throttled most of the time do
 * not contend with each other. Deciding to log a message takes a lock without ever waiting for it:
 * if another thread holds it, that thread is deciding for the same interval, and the call counts as
 * suppressed. The lock is never held while logging.
 */
public class ThrottledLogger implements Logger {

  private final LongAdder suppressedCalls = new LongAdder();
  private final Lock lock;

  // written while holding the lock, read without it to suppress calls
  private volatile long lastLogTime;

  @GuardedBy("lock")
  private long streakStartTime;

  private final Logger log;
  private final long intervalMillis;
  private final long streakGapMillis;
  private final InstantSource clock;

  public ThrottledLogger(final Logger log, final Duration interval) {
    this(log, interval, InstantSource.system());
  }

  ThrottledLogger(final Logger log, final Duration interval, final InstantSource clock) {
    this(log, interval, clock, new ReentrantLock());
  }

  ThrottledLogger(
      final Logger log, final Duration interval, final InstantSource clock, final Lock lock) {
    this.log = log;
    this.clock = clock;
    this.lock = lock;
    intervalMillis = interval.toMillis();
    streakGapMillis = 2 * intervalMillis;
  }

  @Override
  public String getName() {
    return log.getName();
  }

  @Override
  public boolean isTraceEnabled() {
    return log.isTraceEnabled();
  }

  @Override
  public void trace(final String s) {
    checkAndLog(suffix -> log.trace(s + suffix));
  }

  @Override
  public void trace(final String s, final Object o) {
    checkAndLog(suffix -> log.trace(s + suffix, o));
  }

  @Override
  public void trace(final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.trace(s + suffix, o, o1));
  }

  @Override
  public void trace(final String s, final Object... objects) {
    checkAndLog(suffix -> log.trace(s + suffix, objects));
  }

  @Override
  public void trace(final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.trace(s + suffix, throwable));
  }

  @Override
  public boolean isTraceEnabled(final Marker marker) {
    return log.isTraceEnabled(marker);
  }

  @Override
  public void trace(final Marker marker, final String s) {
    checkAndLog(suffix -> log.trace(marker, s + suffix));
  }

  @Override
  public void trace(final Marker marker, final String s, final Object o) {
    checkAndLog(suffix -> log.trace(marker, s + suffix, o));
  }

  @Override
  public void trace(final Marker marker, final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.trace(marker, s + suffix, o, o1));
  }

  @Override
  public void trace(final Marker marker, final String s, final Object... objects) {
    checkAndLog(suffix -> log.trace(marker, s + suffix, objects));
  }

  @Override
  public void trace(final Marker marker, final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.trace(marker, s + suffix, throwable));
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public void debug(final String s) {
    checkAndLog(suffix -> log.debug(s + suffix));
  }

  @Override
  public void debug(final String s, final Object o) {
    checkAndLog(suffix -> log.debug(s + suffix, o));
  }

  @Override
  public void debug(final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.debug(s + suffix, o, o1));
  }

  @Override
  public void debug(final String s, final Object... objects) {
    checkAndLog(suffix -> log.debug(s + suffix, objects));
  }

  @Override
  public void debug(final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.debug(s + suffix, throwable));
  }

  @Override
  public boolean isDebugEnabled(final Marker marker) {
    return log.isDebugEnabled(marker);
  }

  @Override
  public void debug(final Marker marker, final String s) {
    checkAndLog(suffix -> log.debug(marker, s + suffix));
  }

  @Override
  public void debug(final Marker marker, final String s, final Object o) {
    checkAndLog(suffix -> log.debug(marker, s + suffix, o));
  }

  @Override
  public void debug(final Marker marker, final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.debug(marker, s + suffix, o, o1));
  }

  @Override
  public void debug(final Marker marker, final String s, final Object... objects) {
    checkAndLog(suffix -> log.debug(marker, s + suffix, objects));
  }

  @Override
  public void debug(final Marker marker, final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.debug(marker, s + suffix, throwable));
  }

  @Override
  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  @Override
  public void info(final String s) {
    checkAndLog(suffix -> log.info(s + suffix));
  }

  @Override
  public void info(final String s, final Object o) {
    checkAndLog(suffix -> log.info(s + suffix, o));
  }

  @Override
  public void info(final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.info(s + suffix, o, o1));
  }

  @Override
  public void info(final String s, final Object... objects) {
    checkAndLog(suffix -> log.info(s + suffix, objects));
  }

  @Override
  public void info(final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.info(s + suffix, throwable));
  }

  @Override
  public boolean isInfoEnabled(final Marker marker) {
    return log.isInfoEnabled(marker);
  }

  @Override
  public void info(final Marker marker, final String s) {
    checkAndLog(suffix -> log.info(marker, s + suffix));
  }

  @Override
  public void info(final Marker marker, final String s, final Object o) {
    checkAndLog(suffix -> log.info(marker, s + suffix, o));
  }

  @Override
  public void info(final Marker marker, final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.info(marker, s + suffix, o, o1));
  }

  @Override
  public void info(final Marker marker, final String s, final Object... objects) {
    checkAndLog(suffix -> log.info(marker, s + suffix, objects));
  }

  @Override
  public void info(final Marker marker, final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.info(marker, s + suffix, throwable));
  }

  @Override
  public boolean isWarnEnabled() {
    return log.isWarnEnabled();
  }

  @Override
  public void warn(final String s) {
    checkAndLog(suffix -> log.warn(s + suffix));
  }

  @Override
  public void warn(final String s, final Object o) {
    checkAndLog(suffix -> log.warn(s + suffix, o));
  }

  @Override
  public void warn(final String s, final Object... objects) {
    checkAndLog(suffix -> log.warn(s + suffix, objects));
  }

  @Override
  public void warn(final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.warn(s + suffix, o, o1));
  }

  @Override
  public void warn(final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.warn(s + suffix, throwable));
  }

  @Override
  public boolean isWarnEnabled(final Marker marker) {
    return log.isWarnEnabled(marker);
  }

  @Override
  public void warn(final Marker marker, final String s) {
    checkAndLog(suffix -> log.warn(marker, s + suffix));
  }

  @Override
  public void warn(final Marker marker, final String s, final Object o) {
    checkAndLog(suffix -> log.warn(marker, s + suffix, o));
  }

  @Override
  public void warn(final Marker marker, final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.warn(marker, s + suffix, o, o1));
  }

  @Override
  public void warn(final Marker marker, final String s, final Object... objects) {
    checkAndLog(suffix -> log.warn(marker, s + suffix, objects));
  }

  @Override
  public void warn(final Marker marker, final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.warn(marker, s + suffix, throwable));
  }

  @Override
  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  @Override
  public void error(final String s) {
    checkAndLog(suffix -> log.error(s + suffix));
  }

  @Override
  public void error(final String s, final Object o) {
    checkAndLog(suffix -> log.error(s + suffix, o));
  }

  @Override
  public void error(final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.error(s + suffix, o, o1));
  }

  @Override
  public void error(final String s, final Object... objects) {
    checkAndLog(suffix -> log.error(s + suffix, objects));
  }

  @Override
  public void error(final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.error(s + suffix, throwable));
  }

  @Override
  public boolean isErrorEnabled(final Marker marker) {
    return log.isErrorEnabled(marker);
  }

  @Override
  public void error(final Marker marker, final String s) {
    checkAndLog(suffix -> log.error(marker, s + suffix));
  }

  @Override
  public void error(final Marker marker, final String s, final Object o) {
    checkAndLog(suffix -> log.error(marker, s + suffix, o));
  }

  @Override
  public void error(final Marker marker, final String s, final Object o, final Object o1) {
    checkAndLog(suffix -> log.error(marker, s + suffix, o, o1));
  }

  @Override
  public void error(final Marker marker, final String s, final Object... objects) {
    checkAndLog(suffix -> log.error(marker, s + suffix, objects));
  }

  @Override
  public void error(final Marker marker, final String s, final Throwable throwable) {
    checkAndLog(suffix -> log.error(marker, s + suffix, throwable));
  }

  private void checkAndLog(final Consumer<String> logger) {
    final var decision = nextDecision();
    if (decision.log()) {
      logger.accept(decision.suffix());
    }
  }

  private Decision nextDecision() {
    final var currentTime = clock.millis();
    if (currentTime - lastLogTime < intervalMillis) {
      suppressedCalls.increment();
      return Decision.SUPPRESS;
    }

    if (!lock.tryLock()) {
      suppressedCalls.increment();
      return Decision.SUPPRESS;
    }

    try {
      final var lastTime = lastLogTime;
      if (currentTime - lastTime < intervalMillis) {
        // another thread logged while this one was waiting for the lock
        suppressedCalls.increment();
        return Decision.SUPPRESS;
      }

      if (lastTime == 0 || currentTime - lastTime > streakGapMillis) {
        streakStartTime = currentTime;
      }
      // drain the count before publishing the new log time: calls suppressed by this message
      // belong to the next one
      final var suppressed = suppressedCalls.sumThenReset();
      lastLogTime = currentTime;
      return suppressed > 0 ? new Decision(true, suffix(suppressed, currentTime)) : Decision.LOG;
    } finally {
      lock.unlock();
    }
  }

  private String suffix(final long suppressed, final long currentTime) {
    // the wall clock can jump backwards, never report a negative duration
    final var streakMillis = Math.max(0, currentTime - streakStartTime);
    final var streak = streakMillis < 1000 ? "" : ", ongoing for " + formatDuration(streakMillis);
    return " [" + suppressed + " suppressed since last log" + streak + "]";
  }

  private static String formatDuration(final long millis) {
    final var seconds = millis / 1000;
    if (seconds < 60) {
      return seconds + "s";
    }
    final var minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + "m " + seconds % 60 + "s";
    }
    return minutes / 60 + "h " + minutes % 60 + "m";
  }

  /**
   * Whether to log a call and what to append to its message. The common outcomes are shared
   * constants, so that suppressed calls, which can be frequent, do not allocate. Only a message
   * with a suffix needs a new instance, next to the suffix string itself.
   */
  private record Decision(boolean log, String suffix) {
    static final Decision SUPPRESS = new Decision(false, "");
    static final Decision LOG = new Decision(true, "");
  }
}
