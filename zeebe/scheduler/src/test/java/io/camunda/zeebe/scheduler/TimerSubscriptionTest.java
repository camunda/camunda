/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class TimerSubscriptionTest {

  @Test
  void shouldRecordDelayedTimerExpiryOnMonotonicClock() {
    // given
    final var subscription =
        new DelayedTimerSubscription(mockJob(), 1, TimeUnit.MILLISECONDS, false);
    final var before = System.nanoTime();

    // when
    subscription.onTimerExpired(TimeUnit.MILLISECONDS, System.currentTimeMillis());

    // then
    assertThat(subscription.getTimerExpiredAt()).isBetween(before, System.nanoTime());
  }

  @Test
  void shouldRecordStampedTimerExpiryOnMonotonicClock() {
    // given
    final var subscription = new StampedTimerSubscription(mockJob(), System.currentTimeMillis());
    final var before = System.nanoTime();

    // when
    subscription.onTimerExpired(TimeUnit.MILLISECONDS, System.currentTimeMillis());

    // then
    assertThat(subscription.getTimerExpiredAt()).isBetween(before, System.nanoTime());
  }

  private ActorJob mockJob() {
    final var job = mock(ActorJob.class);
    when(job.getTask()).thenReturn(mock(ActorTask.class));
    return job;
  }
}
