/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorCondition;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public final class ConditionalActionsTest {
  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotTriggerActionIfConditionNotTriggered() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // then
    verify(action, never()).run();
  }

  @Test
  public void shouldTriggerActionIfConditionTriggered() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    condition.get().signal();
    scheduler.workUntilDone();

    // then
    verify(action, times(1)).run();
  }

  @Test
  public void shouldTriggerActionOnMultipleSubsequentTriggers() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when then
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final ActorCondition actorCondition = condition.get();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(1)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(2)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(3)).run();

    actorCondition.signal();
    scheduler.workUntilDone();
    verify(action, times(4)).run();
  }

  @Test
  public void shouldTriggerActionOnMultipleSubsequentTriggersConcurrently() {
    // given
    final Runnable action = mock(Runnable.class);
    final AtomicReference<ActorCondition> condition = new AtomicReference<ActorCondition>(null);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("test", action));
          }
        };

    // when then
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final ActorCondition actorCondition = condition.get();

    actorCondition.signal();
    actorCondition.signal();
    actorCondition.signal();
    actorCondition.signal();

    scheduler.workUntilDone();
    verify(action, times(4)).run();
  }

  @Test
  public void shouldTerminateOnFollowUpAndYield() {
    // given
    final AtomicInteger invocations = new AtomicInteger(0);
    final AtomicReference<ActorCondition> condition = new AtomicReference<>();

    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            condition.set(actor.onCondition("foo", this::onCondition));
          }

          protected void onCondition() {
            invocations.incrementAndGet();
            actor.run(this::doNothing);
            actor.yieldThread();
          }

          protected void doNothing() {}
        };

    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    condition.get().signal();
    scheduler.workUntilDone();

    // then
    assertThat(invocations.get()).isEqualTo(1);
  }
}
