/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import static org.mockito.Mockito.when;

import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import java.time.Duration;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

@RunWith(MockitoJUnitRunner.class)
public class DelayedHealthIndicatorTest {

  private static final Duration TEST_MAX_DOWNTIME = Duration.ofMillis(10);

  @Mock private AbstractHealthIndicator<HealthResult> mockHealthIndicator;

  @Test
  public void shouldRejectNullHealthIndicatorInConstructor() {
    Assertions.assertThatThrownBy(() -> new DelayedHealthIndicator(null, TEST_MAX_DOWNTIME))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectNullMaxDowntimeInConstructor() {
    Assertions.assertThatThrownBy(() -> new DelayedHealthIndicator(mockHealthIndicator, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectNegativeMaxDowntimeInConstructor() {
    Assertions.assertThatThrownBy(
            () -> new DelayedHealthIndicator(mockHealthIndicator, Duration.ofMillis(-50)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReportDownHealthStatusIfAskedBeforeDelegateHealthIndicatorWasCalled() {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);

    // when
    final var actualHealth = sutDelayedHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    Assertions.assertThat(healthResult).isNotNull();
    Assertions.assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  public void
      shouldReportHealthStatusOfDelegateHealthIndicatorIfBackendHealthIndicatorWasNeverUp() {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);

    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.DOWN).build()));

    // when
    sutDelayedHealthIndicator.checkHealth();
    final var actualHealth = sutDelayedHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    Assertions.assertThat(healthResult).isNotNull();
    Assertions.assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  public void
      shouldReportHealthStatusUpWhenBackendHealthIndicatorWasUpInThePastAndIsTemporarilyDown() {
    // given
    final var testClock = new TestCLock();
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME, testClock);
    // backend health indicator was up in the past
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.UP).build()));
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.DOWN).build()));
    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() - 1);
    sutDelayedHealthIndicator.checkHealth();

    final var actualHealth = sutDelayedHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    // delayed health indicator is still up
    Assertions.assertThat(healthResult).isNotNull();
    Assertions.assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.UP);
  }

  @Test
  public void
      shouldReportHealthStatusDownWhenBackendHealthIndicatorWasUpInThePastAndIsDownForMoreThanMaxDowntime()
          throws InterruptedException {
    // given
    final var testClock = new TestCLock();
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME, testClock);
    // backend health indicator was up in the past
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.UP).build()));
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.DOWN).build()));
    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() - 1);
    sutDelayedHealthIndicator.checkHealth();

    final var actualHealthImmediate = sutDelayedHealthIndicator.getResult();
    final var healthResultImmediate =
        Mono.from(actualHealthImmediate).block(Duration.ofMillis(5000));

    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() + 1);
    sutDelayedHealthIndicator.checkHealth();
    final var actualHealthAfterDelay = sutDelayedHealthIndicator.getResult();
    final var healthResultAfterDelay =
        Mono.from(actualHealthAfterDelay).block(Duration.ofMillis(5000));

    // then
    // immediate health report was up
    Assertions.assertThat(healthResultImmediate).isNotNull();
    Assertions.assertThat(healthResultImmediate.getStatus()).isEqualTo(HealthStatus.UP);

    // delayed health report was down
    Assertions.assertThat(healthResultAfterDelay).isNotNull();
    Assertions.assertThat(healthResultAfterDelay.getStatus()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  public void
      shouldReportHealthStatusUpWhenBackendHealthIndicatorGoesDownTemporarilyButComesUpBeforeTheMaxDowntimeExpired()
          throws InterruptedException {
    // given
    final var testClock = new TestCLock();
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME, testClock);
    // backend health indicator was up in the past
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.UP).build()));
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.DOWN).build()));
    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() - 5);
    sutDelayedHealthIndicator.checkHealth();
    final var actualHealthImmediate = sutDelayedHealthIndicator.getResult();
    final var healthResultImmediate =
        Mono.from(actualHealthImmediate).block(Duration.ofMillis(5000));

    // backend health indicator is up again
    when(mockHealthIndicator.getResult())
        .thenReturn(
            Mono.just(HealthResult.builder(getClass().getSimpleName(), HealthStatus.UP).build()));
    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() - 1);
    sutDelayedHealthIndicator.checkHealth();

    testClock.setTime(TEST_MAX_DOWNTIME.toMillis() + 1);
    final var actualHealthAfterDelay = sutDelayedHealthIndicator.getResult();
    final var healthResultAfterDelay =
        Mono.from(actualHealthAfterDelay).block(Duration.ofMillis(5000));

    // then
    // immediate health report was up
    Assertions.assertThat(healthResultImmediate).isNotNull();
    Assertions.assertThat(healthResultImmediate.getStatus()).isEqualTo(HealthStatus.UP);

    // delayed health report is also up
    Assertions.assertThat(healthResultAfterDelay).isNotNull();
    Assertions.assertThat(healthResultAfterDelay.getStatus()).isEqualTo(HealthStatus.UP);
  }

  private static final class TestCLock implements Supplier<Long> {
    private long time = 0;

    public void setTime(final long time) {
      this.time = time;
    }

    @Override
    public Long get() {
      return time;
    }
  }
}
