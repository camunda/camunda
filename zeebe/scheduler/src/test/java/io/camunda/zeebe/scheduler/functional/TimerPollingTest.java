/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.functional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorTimerQueue;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class TimerPollingTest {

  /**
   * Mimics a busy actor thread: the millisecond change has already been consumed by an earlier
   * update() call, e.g. the one after a task execution, so update() never reports it again.
   */
  private final ConsumedUpdateClock clock = new ConsumedUpdateClock();

  @RegisterExtension
  final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension(
          builder -> builder.setActorClock(clock).setActorTimerQueue(new ActorTimerQueue(clock)));

  @Test
  void shouldFireTimerEvenIfUpdateDoesNotReportTimeChange() {
    // given
    final Runnable action = mock(Runnable.class);
    clock.time.set(100);
    actorScheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.schedule(Duration.ofMillis(10), action);
          }
        });
    actorScheduler.workUntilDone();
    verify(action, never()).run();

    // when
    clock.time.addAndGet(10);
    actorScheduler.workUntilDone();

    // then
    verify(action).run();
  }

  private static final class ConsumedUpdateClock implements ActorClock {
    private final AtomicLong time = new AtomicLong();

    @Override
    public boolean update() {
      return false;
    }

    @Override
    public long getTimeMillis() {
      return time.get();
    }

    @Override
    public long getNanosSinceLastMillisecond() {
      return 0;
    }

    @Override
    public long getNanoTime() {
      return time.get() * 1_000_000;
    }
  }
}
