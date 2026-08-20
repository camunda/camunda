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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class TimedActionsTest {
  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  private static final class TimerActor extends Actor {

    private final Function<ActorControl, ScheduledTimer> action;

    private ScheduledTimer scheduledTimer;

    private TimerActor(final Function<ActorControl, ScheduledTimer> action) {
      this.action = action;
    }

    @Override
    protected void onActorStarted() {
      scheduledTimer = action.apply(actor);
    }

    public ActorFuture<Void> cancelTimer() {
      return actor.call(() -> scheduledTimer.cancel());
    }
  }

  @Nested
  class DelayedTimerTests {
    @Test
    public void shouldNotRunActionIfDeadlineNotReached() throws InterruptedException {
      // given
      final Runnable action = mock(Runnable.class);
      final Actor actor =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.schedule(Duration.ofMillis(10), action);
            }
          };

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();

      // then
      verify(action, never()).run();
    }

    @Test
    public void shouldRunActionWhenDeadlineReached() throws InterruptedException {
      // given
      final Runnable action = mock(Runnable.class);
      final Actor actor =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.schedule(Duration.ofMillis(10), action);
            }
          };

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();

      // then
      verify(action, times(1)).run();
    }

    @Test
    public void shouldRunAtFixedRate() throws InterruptedException {
      // given
      final Runnable action = mock(Runnable.class);
      final Actor actor =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.runAtFixedRate(Duration.ofMillis(10), action);
            }
          };

      // when then
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();

      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();
      verify(action, times(1)).run();

      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();
      verify(action, times(2)).run();

      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();
      verify(action, times(3)).run();
    }

    @Test
    public void shouldCancelRunDelayed() {
      // given
      final Runnable action = mock(Runnable.class);
      final TimerActor actor =
          new TimerActor(actorControl -> actorControl.schedule(Duration.ofMillis(10), action));

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();
      actor.cancelTimer();
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();

      // then
      verify(action, times(0)).run();
    }

    @Test
    public void shouldCancelRunDelayedAfterExecution() {
      // given
      final Runnable action = mock(Runnable.class);
      final var actor =
          new TimerActor(actorControl -> actorControl.schedule(Duration.ofMillis(10), action));

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();

      // make timer run
      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();

      // when
      actor.cancelTimer();
      actorScheduler.workUntilDone();

      // then
      // no exception has been thrown
    }

    @Test
    public void shouldCancelRunAtFixedRate() {
      // given
      final Runnable action = mock(Runnable.class);
      final TimerActor actor =
          new TimerActor(
              actorControl -> actorControl.runAtFixedRate(Duration.ofMillis(10), action));

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();
      actor.cancelTimer();
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofMillis(10));
      actorScheduler.workUntilDone();

      // then
      verify(action, times(0)).run();
    }
  }

  @Nested
  class StampedTimerTests {
    @Test
    public void shouldNotRunActionIfDeadlineNotReached() throws InterruptedException {
      // given
      final Runnable action = mock(Runnable.class);
      final Actor actor =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.runAt(1000, action);
            }
          };

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();

      // then
      verify(action, never()).run();
    }

    @Test
    public void shouldRunActionWhenDeadlineReached() throws InterruptedException {
      // given
      final Runnable action = mock(Runnable.class);
      final Actor actor =
          new Actor() {
            @Override
            protected void onActorStarted() {
              actor.runAt(1000, action);
            }
          };

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofMillis(900));
      actorScheduler.workUntilDone();

      // then
      verify(action, times(1)).run();
    }

    @Test
    public void shouldCancelRunAt() {
      // given
      final Runnable action = mock(Runnable.class);
      final TimerActor actor = new TimerActor(actorControl -> actorControl.runAt(1000, action));

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();
      actor.cancelTimer();
      actorScheduler.workUntilDone();
      actorScheduler.updateClock(Duration.ofMillis(900));
      actorScheduler.workUntilDone();

      // then
      verify(action, times(0)).run();
    }

    @Test
    public void shouldCancelRunDelayedAfterExecution() {
      // given
      final Runnable action = mock(Runnable.class);
      final var actor = new TimerActor(actorControl -> actorControl.runAt(1000, action));

      // when
      actorScheduler.setClockTime(100);
      actorScheduler.submitActor(actor);
      actorScheduler.workUntilDone();

      // make timer run
      actorScheduler.updateClock(Duration.ofMillis(900));
      actorScheduler.workUntilDone();

      // when
      actor.cancelTimer();
      actorScheduler.workUntilDone();

      // then
      // no exception has been thrown
    }
  }
}
