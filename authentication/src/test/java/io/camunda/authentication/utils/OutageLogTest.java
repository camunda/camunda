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
