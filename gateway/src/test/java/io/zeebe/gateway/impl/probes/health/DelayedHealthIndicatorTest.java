/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

@RunWith(MockitoJUnitRunner.class)
public class DelayedHealthIndicatorTest {

  private static final Duration TEST_MAX_DOWNTIME = Duration.ofMillis(50);

  @Mock private HealthIndicator mockHealthIndicator;

  @Test
  public void shouldRejectNullHealthIndicatorInConstructor() {
    assertThatThrownBy(() -> new DelayedHealthIndicator(null, TEST_MAX_DOWNTIME))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectNullMaxDowntimeInConstructor() {
    assertThatThrownBy(() -> new DelayedHealthIndicator(mockHealthIndicator, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectNegativeMaxDowntimeInConstructor() {
    assertThatThrownBy(
            () -> new DelayedHealthIndicator(mockHealthIndicator, Duration.ofMillis(-50)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReportUnknownHealthStatusIfAskedBeforeDelegateHealthIndicatorWasCalled() {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);

    // when
    final Health actualHealth = sutDelayedHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UNKNOWN);
  }

  @Test
  public void
      shouldReportHealthStatusOfDelegateHealthIndicatorIfBackendHealthIndicatorWasNeverUp() {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);

    when(mockHealthIndicator.health()).thenReturn(Health.down().build());

    // when
    sutDelayedHealthIndicator.checkHealth();
    final Health actualHealth = sutDelayedHealthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void
      shouldReportHealthStatusUpWhenBackendHealthIndicatorWasUpInThePastAndIsTemporarilyDown() {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);
    // backend health indicator was up in the past
    when(mockHealthIndicator.health()).thenReturn(Health.up().build());
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.health()).thenReturn(Health.down().build());
    sutDelayedHealthIndicator.checkHealth();

    final Health actualHealth = sutDelayedHealthIndicator.health();

    // then
    // delayed health indicator is still up
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  public void
      shouldReportHealthStatusDownWhenBackendHealthIndicatorWasUpInThePastAndIsDownForMoreThanMaxDowntime()
          throws InterruptedException {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);
    // backend health indicator was up in the past
    when(mockHealthIndicator.health()).thenReturn(Health.up().build());
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.health()).thenReturn(Health.down().build());
    sutDelayedHealthIndicator.checkHealth();

    final Health actualHealthImmediate = sutDelayedHealthIndicator.health();

    // wait for more then the configured max downtime
    Thread.sleep(100);
    sutDelayedHealthIndicator.checkHealth();
    final Health actualHealthAfterDelay = sutDelayedHealthIndicator.health();

    // then
    // immediate health report was up
    assertThat(actualHealthImmediate).isNotNull();
    assertThat(actualHealthImmediate.getStatus()).isEqualTo(Status.UP);

    // delayed health report was down
    assertThat(actualHealthAfterDelay).isNotNull();
    assertThat(actualHealthAfterDelay.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  public void
      shouldReportHealthStatusUpWhenBackendHealthIndicatorGoesDownTemporarilyButComesUpBeforeTheMaxDowntimeExpired()
          throws InterruptedException {
    // given
    final var sutDelayedHealthIndicator =
        new DelayedHealthIndicator(mockHealthIndicator, TEST_MAX_DOWNTIME);
    // backend health indicator was up in the past
    when(mockHealthIndicator.health()).thenReturn(Health.up().build());
    sutDelayedHealthIndicator.checkHealth();

    // when
    // backend health indicator goes down
    when(mockHealthIndicator.health()).thenReturn(Health.down().build());
    sutDelayedHealthIndicator.checkHealth();
    final Health actualHealthImmediate = sutDelayedHealthIndicator.health();

    // backend health indicator is up again
    when(mockHealthIndicator.health()).thenReturn(Health.up().build());
    sutDelayedHealthIndicator.checkHealth();

    // wait for more then the configured max downtime
    Thread.sleep(100);

    final Health actualHealthAfterDelay = sutDelayedHealthIndicator.health();

    // then
    // immediate health report was up
    assertThat(actualHealthImmediate).isNotNull();
    assertThat(actualHealthImmediate.getStatus()).isEqualTo(Status.UP);

    // delayed health report is also up
    assertThat(actualHealthAfterDelay).isNotNull();
    assertThat(actualHealthAfterDelay.getStatus()).isEqualTo(Status.UP);
  }
}
