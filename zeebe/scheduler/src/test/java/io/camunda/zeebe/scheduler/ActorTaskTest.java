/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ActorTaskTest {

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Test
  void shouldWakeUpWhenSubscriptionTriggersWhileGoingToWait() {
    // given
    final var actor = new Actor() {};
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final var task = new AtomicReference<ActorTask>();
    final var subscription = new TriggerableSubscription();
    actor.run(
        () -> {
          task.set(ActorThread.current().getCurrentJob().getTask());
          task.get().addSubscription(subscription);
        });
    scheduler.workUntilDone();
    subscription.triggered = true;

    // when
    final var wokenUp = task.get().tryWait();

    // then
    assertThat(wokenUp).isTrue();
  }

  @Test
  void shouldWakeUpWhenSubscriptionPollThrowsWhileGoingToWait() {
    // given
    final var actor = new Actor() {};
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final var task = new AtomicReference<ActorTask>();
    final var subscription = new TriggerableSubscription();
    actor.run(
        () -> {
          task.set(ActorThread.current().getCurrentJob().getTask());
          task.get().addSubscription(subscription);
        });
    scheduler.workUntilDone();
    subscription.failOnPoll = true;

    // when
    final var wokenUp = task.get().tryWait();

    // then
    assertThat(wokenUp).isTrue();
  }

  private static final class TriggerableSubscription implements ActorSubscription {
    private boolean triggered;
    private boolean failOnPoll;

    @Override
    public boolean triggersInPhase(final ActorLifecyclePhase phase) {
      return true;
    }

    @Override
    public boolean poll() {
      if (failOnPoll) {
        throw new IllegalStateException("poll failure");
      }
      final var wasTriggered = triggered;
      triggered = false;
      return wasTriggered;
    }

    @Override
    public ActorJob getJob() {
      return null;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }
  }
}
