/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.function.Function;
import org.junit.Rule;
import org.junit.Test;

public final class TimedActionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

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
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

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
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

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
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(1)).run();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(2)).run();

    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(3)).run();
  }

  @Test
  public void shouldCancelRunDelayed() {
    // given
    final Runnable action = mock(Runnable.class);
    final TimerActor actor =
        new TimerActor(actorControl -> actorControl.schedule(Duration.ofMillis(10), action));

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    actor.cancelTimer();
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

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
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // make timer run
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // when
    actor.cancelTimer();
    schedulerRule.workUntilDone();

    // then
    // no exception has been thrown
  }

  @Test
  public void shouldCancelRunAtFixedRate() {
    // given
    final Runnable action = mock(Runnable.class);
    final TimerActor actor =
        new TimerActor(actorControl -> actorControl.runAtFixedRate(Duration.ofMillis(10), action));

    // when
    schedulerRule.getClock().setCurrentTime(100);
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    actor.cancelTimer();
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

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
}
