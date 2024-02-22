/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import io.camunda.zeebe.util.exception.RecoverableException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class RecoverableRetryStrategyTest {

  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private RecoverableRetryStrategy recoverableRetryStrategy;
  private ActorControl actorControl;
  private ActorFuture<Boolean> resultFuture;

  @Before
  public void setUp() {
    final ControllableActor actor = new ControllableActor();
    actorControl = actor.getActor();
    recoverableRetryStrategy = new RecoverableRetryStrategy(actorControl);

    schedulerRule.submitActor(actor);
  }

  @Test
  public void shouldRetryOnException() throws Exception {
    // given
    final AtomicInteger count = new AtomicInteger(0);
    final AtomicBoolean toggle = new AtomicBoolean(false);

    // when
    actorControl.run(
        () -> {
          resultFuture =
              recoverableRetryStrategy.runWithRetry(
                  () -> {
                    toggle.set(!toggle.get());
                    if (toggle.get()) {
                      throw new RecoverableException("expected");
                    }
                    return count.incrementAndGet() == 10;
                  });
        });

    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture.isDone()).isTrue();
    assertThat(resultFuture.get()).isTrue();
  }

  private final class ControllableActor extends Actor {
    public ActorControl getActor() {
      return actor;
    }
  }
}
