/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class OutageLogTest {

  // OutageLog forwards its own Object[] on, so every call lands on the varargs overload. Dropping
  // the explicit array here would verify Logger#warn(String, Object) instead — a method that is
  // never invoked, so the verification fails however the class behaves.
  private static final Object[] BOOM = {"boom"};
  private static final Object[] NO_ARGS = {};

  @Mock private Logger logger;

  @Test
  void shouldWarnOnlyOnTheFailureThatStartsTheOutage() {
    // given
    final var outageLog = new OutageLog(logger);

    // when — the same failure recurs on every request for the duration of the outage
    outageLog.failure("lookup failed: {}", "boom");
    outageLog.failure("lookup failed: {}", "boom");
    outageLog.failure("lookup failed: {}", "boom");

    // then
    verify(logger).warn("lookup failed: {}", BOOM);
    verify(logger, times(2)).debug("lookup failed: {}", BOOM);
  }

  @Test
  void shouldReportRecoveryOnlyOnTheFirstSuccessAfterAnOutage() {
    // given — an open outage
    final var outageLog = new OutageLog(logger);
    outageLog.failure("lookup failed");

    // when
    outageLog.recovery("lookup recovered");
    outageLog.recovery("lookup recovered");

    // then
    verify(logger).info("lookup recovered", NO_ARGS);
  }

  @Test
  void shouldStaySilentOnSuccessWhenNoOutageIsOpen() {
    // given
    final var outageLog = new OutageLog(logger);

    // when
    outageLog.recovery("lookup recovered");

    // then
    verifyNoInteractions(logger);
  }

  @Test
  void shouldWarnAgainOnceTheOutageOutlastsTheRepeatInterval() {
    // given — an outage that never recovers. Not every permanent condition is classified
    // non-transient: a renamed index reaches ES as an ElasticsearchException, which the search
    // client maps to SEARCH_SERVER_FAILED, so it is retried and degraded like a blip. Collapsing to
    // a single WARN would leave that condition invisible for as long as it lasts.
    final var clock = new AtomicLong();
    final var outageLog = new OutageLog(logger, Duration.ofMinutes(5), clock::get);

    // when — the failure recurs for eleven minutes
    outageLog.failure("lookup failed");
    clock.addAndGet(Duration.ofMinutes(4).toNanos());
    outageLog.failure("lookup failed");
    clock.addAndGet(Duration.ofMinutes(1).toNanos());
    outageLog.failure("lookup failed");
    clock.addAndGet(Duration.ofMinutes(6).toNanos());
    outageLog.failure("lookup failed");

    // then — one WARN at the start and one per elapsed interval, the rest at DEBUG
    verify(logger, times(3)).warn("lookup failed", NO_ARGS);
    verify(logger).debug("lookup failed", NO_ARGS);
  }

  @Test
  void shouldRestartTheRepeatIntervalAfterARecovery() {
    // given — an outage reported, recovered, and reopened well inside one interval
    final var clock = new AtomicLong();
    final var outageLog = new OutageLog(logger, Duration.ofMinutes(5), clock::get);
    outageLog.failure("lookup failed");
    clock.addAndGet(Duration.ofMinutes(4).toNanos());
    outageLog.recovery("lookup recovered");
    outageLog.failure("lookup failed");

    // when — a minute passes, which would have been due had the interval kept running
    clock.addAndGet(Duration.ofMinutes(1).toNanos());
    outageLog.failure("lookup failed");

    // then — the second outage gets its own WARN and then its own full interval of quiet, so a
    // flapping dependency cannot warn on every failure by recovering in between
    verify(logger, times(2)).warn("lookup failed", NO_ARGS);
    verify(logger).debug("lookup failed", NO_ARGS);
  }

  @Test
  void shouldWarnAgainOnTheOutageFollowingARecovery() {
    // given — a first outage that has already been reported as recovered
    final var outageLog = new OutageLog(logger);
    outageLog.failure("lookup failed");
    outageLog.recovery("lookup recovered");

    // when — a second, unrelated outage starts
    outageLog.failure("lookup failed");

    // then — operators see both outages, not just the first
    verify(logger, times(2)).warn("lookup failed", NO_ARGS);
    verify(logger, never()).debug("lookup failed", NO_ARGS);
  }
}
