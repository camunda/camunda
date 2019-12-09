/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;

public class CallableExecutionTest {
  @Rule public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

  @Test
  public void shouldCompleteFutureExceptionallyWhenSubmittedDuringActorClosedJob()
      throws InterruptedException, BrokenBarrierException {
    // given
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final CloseableActor actor =
        new CloseableActor() {
          @Override
          protected void onActorClosed() {
            try {
              barrier.await(); // signal arrival at barrier
              barrier.await(); // wait for continuation
            } catch (InterruptedException | BrokenBarrierException e) {
              throw new RuntimeException(e);
            }
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    barrier.await(); // wait for actor to reach onActorClosed callback

    final ActorFuture<Void> future = actor.doCall();

    // when
    barrier.await(); // signal actor to continue

    // then
    TestUtil.waitUntil(() -> future.isDone());
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  class CloseableActor extends Actor {
    ActorFuture<Void> doCall() {
      return actor.call(() -> {});
    }
  }
}
