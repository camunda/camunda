/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.iobound;

import static io.camunda.zeebe.util.sched.SchedulingHints.ioBound;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorThread;
import io.camunda.zeebe.util.sched.ActorThreadGroup;
import io.camunda.zeebe.util.sched.CpuThreadGroup;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

public final class IoBoundActorsIntegrationTest {
  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

  @Test
  public void shouldRunIoBoundActor() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicReference<ActorThreadGroup> threadGroupRef =
        new AtomicReference<ActorThreadGroup>();
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            threadGroupRef.set(ActorThread.current().getActorThreadGroup());
          }
        };

    // when
    schedulerRule.get().submitActor(actor, ioBound()).join();

    // then
    assertThat(threadGroupRef.get()).isEqualTo(ioBoundActorThreads);
  }

  @Test
  public void shouldStayOnIoBoundThreadGroupWhenInteractingWithCpuBound() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicBoolean isOnWrongThreadGroup = new AtomicBoolean();
    final CallableActor callableActor = new CallableActor(isOnWrongThreadGroup);
    final Actor ioBoundActor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            for (int i = 0; i < 1_000; i++) {
              actor.runOnCompletion(callableActor.doCall(), this::callback);
            }
          }

          protected void callback(final Void res, final Throwable t) {
            if (ActorThread.current().getActorThreadGroup() != ioBoundActorThreads) {
              isOnWrongThreadGroup.set(true);
            }
          }
        };

    // when
    schedulerRule.submitActor(callableActor).join();
    schedulerRule.get().submitActor(ioBoundActor, ioBound()).join();

    // then
    assertThat(isOnWrongThreadGroup).isFalse();
  }

  @Test
  public void shouldStayOnIoBoundThreadGroupWhenInteractingWithCpuBoundOnBlockingPhase() {
    final ActorThreadGroup ioBoundActorThreads =
        schedulerRule.getBuilder().getIoBoundActorThreads();

    // given
    final AtomicBoolean isOnWrongThreadGroup = new AtomicBoolean();
    final CallableActor callableActor = new CallableActor(isOnWrongThreadGroup);
    final Actor ioBoundActor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            for (int i = 0; i < 1_000; i++) {
              actor.runOnCompletionBlockingCurrentPhase(callableActor.doCall(), this::callback);
            }
          }

          protected void callback(final Void res, final Throwable t) {
            if (ActorThread.current().getActorThreadGroup() != ioBoundActorThreads) {
              isOnWrongThreadGroup.set(true);
            }
          }
        };

    // when
    schedulerRule.submitActor(callableActor).join();
    schedulerRule.get().submitActor(ioBoundActor, ioBound()).join();

    // then
    assertThat(isOnWrongThreadGroup).isFalse();
  }

  class CallableActor extends Actor {
    private final AtomicBoolean isOnWrongThreadGroup;

    CallableActor(final AtomicBoolean isOnWrongThreadGroup) {
      this.isOnWrongThreadGroup = isOnWrongThreadGroup;
    }

    public ActorFuture<Void> doCall() {
      return actor.call(
          () -> {
            if (!(ActorThread.current().getActorThreadGroup() instanceof CpuThreadGroup)) {
              isOnWrongThreadGroup.set(true);
            }
          });
    }
  }
}
