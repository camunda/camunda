/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class RetryStrategyTest {

  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Parameter public Class<RetryStrategy> retryStrategyClass;
  private RetryStrategy retryStrategy;
  private ActorControl actorControl;
  private ActorFuture<Boolean> resultFuture;

  @Parameters(name = "{index}: {0}")
  public static Object[][] reprocessingTriggers() {
    return new Object[][] {
      new Object[] {RecoverableRetryStrategy.class}, new Object[] {AbortableRetryStrategy.class}
    };
  }

  @Before
  public void setUp() {
    final ControllableActor actor = new ControllableActor();
    actorControl = actor.getActor();

    try {
      final Constructor<RetryStrategy> constructor =
          retryStrategyClass.getConstructor(ActorControl.class);
      retryStrategy = constructor.newInstance(actorControl);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    schedulerRule.submitActor(actor);
  }

  @Test
  public void shouldRunUntilDone() throws Exception {
    // given
    final AtomicInteger count = new AtomicInteger(0);

    // when
    actorControl.run(
        () -> {
          resultFuture = retryStrategy.runWithRetry(() -> count.incrementAndGet() == 10);
        });

    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture.isDone()).isTrue();
    assertThat(resultFuture.get()).isTrue();
  }

  @Test
  public void shouldStopWhenAbortConditionReturnsTrue() throws Exception {
    // given
    final AtomicInteger count = new AtomicInteger(0);

    // when
    actorControl.run(
        () -> {
          resultFuture =
              retryStrategy.runWithRetry(() -> false, () -> count.incrementAndGet() == 10);
        });

    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture.isDone()).isTrue();
    assertThat(resultFuture.get()).isFalse();
  }

  @Test
  public void shouldAbortOnOtherException() {
    // given
    // when
    actorControl.run(
        () ->
            resultFuture =
                retryStrategy.runWithRetry(
                    () -> {
                      throw new RuntimeException("expected");
                    }));

    schedulerRule.workUntilDone();

    // then
    assertThat(resultFuture.isDone()).isTrue();
    assertThat(resultFuture.isCompletedExceptionally()).isTrue();
    assertThat(resultFuture.getException()).isExactlyInstanceOf(RuntimeException.class);
  }

  private static final class ControllableActor extends Actor {
    public ActorControl getActor() {
      return actor;
    }
  }
}
