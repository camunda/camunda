/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class PerMessageThrottledLoggerTest {

  private Logger delegate;
  private AtomicLong currentTime;
  private PerMessageThrottledLogger throttledLogger;

  @BeforeEach
  void setUp() {
    delegate = mock(Logger.class);
    // Enable all log levels by default
    when(delegate.isWarnEnabled()).thenReturn(true);
    when(delegate.isInfoEnabled()).thenReturn(true);
    when(delegate.isErrorEnabled()).thenReturn(true);
    when(delegate.isDebugEnabled()).thenReturn(true);

    currentTime = new AtomicLong(0);
    throttledLogger =
        new PerMessageThrottledLogger(delegate, Duration.ofMillis(100), currentTime::get);
  }

  @Test
  void shouldLogFirstOccurrence() {
    // when
    throttledLogger.warn("Test message {}", "param1");

    // then
    verify(delegate).warn("Test message {}", new Object[] {"param1"});
  }

  @Test
  void shouldThrottleRepeatedMessages() {
    // when - log the same message multiple times rapidly
    throttledLogger.warn("Test message {}", "param1");
    throttledLogger.warn("Test message {}", "param2");
    throttledLogger.warn("Test message {}", "param3");

    // then - should only log once with the first parameters
    verify(delegate).warn("Test message {}", new Object[] {"param1"});
  }

  @Test
  void shouldLogAgainAfterThrottleInterval() {
    // when - log message, advance time, then log again
    throttledLogger.warn("Test message {}", "param1");
    currentTime.addAndGet(150); // Advance time beyond throttle interval (100ms)
    throttledLogger.warn("Test message {}", "param2");

    // then - should log twice
    verify(delegate).warn("Test message {}", new Object[] {"param1"});
    verify(delegate).warn("Test message {}", new Object[] {"param2"});
  }

  @Test
  void shouldThrottleIndependentlyPerMessage() {
    // when - log different message templates
    throttledLogger.warn("Message A {}", "param1");
    throttledLogger.warn("Message B {}", "param1");
    throttledLogger.warn("Message A {}", "param2");
    throttledLogger.warn("Message B {}", "param2");

    // then - each message template should be logged once
    verify(delegate).warn("Message A {}", new Object[] {"param1"});
    verify(delegate).warn("Message B {}", new Object[] {"param1"});
  }

  @Test
  void shouldWorkWithInfoLevel() {
    // when
    throttledLogger.info("Info message {}", "param1");
    throttledLogger.info("Info message {}", "param2");

    // then - should only log once
    verify(delegate).info("Info message {}", new Object[] {"param1"});
  }

  @Test
  void shouldWorkWithErrorLevel() {
    // when
    throttledLogger.error("Error message {}", "param1");
    throttledLogger.error("Error message {}", "param2");

    // then - should only log once
    verify(delegate).error("Error message {}", new Object[] {"param1"});
  }

  @Test
  void shouldWorkWithDebugLevel() {
    // when
    throttledLogger.debug("Debug message {}", "param1");
    throttledLogger.debug("Debug message {}", "param2");

    // then - should only log once
    verify(delegate).debug("Debug message {}", new Object[] {"param1"});
  }

  @Test
  void shouldTrackMultipleMessagesIndependently() {
    // when - log multiple different messages
    throttledLogger.warn("Message 1 {}", "a");
    throttledLogger.warn("Message 2 {}", "b");
    throttledLogger.warn("Message 3 {}", "c");

    // Advance time beyond throttle interval
    currentTime.addAndGet(150);

    throttledLogger.warn("Message 1 {}", "d");
    throttledLogger.warn("Message 2 {}", "e");

    // then - each message should be logged twice (once before and once after the interval)
    verify(delegate).warn("Message 1 {}", new Object[] {"a"});
    verify(delegate).warn("Message 2 {}", new Object[] {"b"});
    verify(delegate).warn("Message 3 {}", new Object[] {"c"});
    verify(delegate).warn("Message 1 {}", new Object[] {"d"});
    verify(delegate).warn("Message 2 {}", new Object[] {"e"});
  }

  @Test
  void shouldClearThrottleState() {
    // when
    throttledLogger.warn("Test message {}", "param1");
    throttledLogger.clearThrottleState();
    throttledLogger.warn("Test message {}", "param2");

    // then - should log twice (once before clear, once after)
    verify(delegate).warn("Test message {}", new Object[] {"param1"});
    verify(delegate).warn("Test message {}", new Object[] {"param2"});
  }

  @Test
  void shouldHandleMessagesWithNoParameters() {
    // when
    throttledLogger.warn("Simple message");
    throttledLogger.warn("Simple message");

    // then - should only log once
    verify(delegate).warn("Simple message", new Object[] {});
  }

  @Test
  void shouldHandleMessagesWithMultipleParameters() {
    // when
    throttledLogger.warn("Message {} and {} and {}", "a", "b", "c");
    throttledLogger.warn("Message {} and {} and {}", "x", "y", "z");

    // then - should only log once (same template)
    verify(delegate).warn("Message {} and {} and {}", new Object[] {"a", "b", "c"});
  }

  @Test
  void shouldNotUpdateStateWhenLogLevelDisabled() {
    // given - disable warn level
    when(delegate.isWarnEnabled()).thenReturn(false);

    // when
    throttledLogger.warn("Test message {}", "param1");
    throttledLogger.warn("Test message {}", "param2");

    // then - no logging should happen and state should not be updated
    verify(delegate, times(2)).isWarnEnabled();
    verifyNoMoreInteractions(delegate);
    assertThat(throttledLogger.getLastLogTimeByMessage()).isEmpty();
  }

  @Test
  void shouldHandleNullMessageTemplate() {
    // when
    throttledLogger.warn(null, "param1");
    throttledLogger.warn(null, "param2");

    // then - should ignore null templates and not log anything
    verify(delegate, times(2)).isWarnEnabled();
    verifyNoMoreInteractions(delegate);
    assertThat(throttledLogger.getLastLogTimeByMessage()).isEmpty();
  }
}
