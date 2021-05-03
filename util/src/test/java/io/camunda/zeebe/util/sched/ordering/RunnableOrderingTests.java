/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;

import io.camunda.zeebe.util.sched.ActorCondition;
import io.camunda.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public final class RunnableOrderingTests {
  private static final String ONE = "one";
  private static final String TWO = "two";
  private static final String THREE = "three";
  private static final String FOUR = "four";
  private static final String FIVE = "five";

  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldRunAllActionsInAnyOrder() {
    // given
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(runnable(ONE));
            actor.run(runnable(TWO));
            actor.run(runnable(THREE));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    // all actions are performed in any order
    assertThat(actor.actions).containsOnly(ONE, TWO, THREE);
  }

  @Test
  public void shouldFinishCurrentRunnableAfterExecutingNext() {
    // given
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.run(runnable(TWO)); // this is executed after the current runnable returns
                  actions.add(ONE);
                });

            actor.run(runnable(THREE));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(ONE, TWO));
    assertThat(actor.actions).containsOnly(ONE, TWO, THREE);
  }

  @Test
  public void submitTest() {
    // given
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.submit(runnable(TWO));
                  actions.add(ONE);
                });

            actor.run(runnable(THREE));
            actor.submit(runnable(FOUR));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    // no guarantee of ordering between (ONE, THREE) and (TWO, FOUR), but the following constraints
    // must hold:
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, FOUR));
  }

  @Test
  public void runOnCompletionFutureTest() {
    // given
    final CompletableActorFuture<Void> future = CompletableActorFuture.completed(null);
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.runOnCompletion(future, futureConsumer(TWO));
                  actions.add(ONE);
                });

            actor.run(runnable(THREE));
            actor.runOnCompletion(future, futureConsumer(FOUR));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    // no guarantee of ordering between (ONE, THREE) and (TWO, FOUR), but the following constraints
    // must hold:
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, FOUR));
  }

  @Test
  public void blockPhaseUntilCompletionFutureTest() {
    // given
    final CompletableActorFuture<Void> future = CompletableActorFuture.completed(null);
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.runOnCompletionBlockingCurrentPhase(future, futureConsumer(TWO));
                  actions.add(ONE);
                });

            actor.run(runnable(THREE));
            actor.runOnCompletionBlockingCurrentPhase(future, futureConsumer(FOUR));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    // no guarantee of ordering between (ONE, THREE) and (TWO, FOUR), but the following constraints
    // must hold:
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, TWO));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, FOUR));
  }

  @Test
  public void runUntilDoneTest() {
    // given
    final CompletableActorFuture<Void> future = CompletableActorFuture.completed(null);
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(runnable(TWO));
            final AtomicInteger count = new AtomicInteger();
            actor.runUntilDone(
                () -> {
                  final int couter = count.incrementAndGet();
                  if (couter > 2) {
                    actor.done();
                  } else {
                    actor.runOnCompletion(future, futureConsumer(FOUR));
                  }

                  actions.add(ONE);
                });
            actor.run(runnable(THREE));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(ONE, ONE, ONE));
    assertThat(actor.actions).containsSequence(newArrayList(FOUR, FOUR));
    assertThat(actor.actions)
        .containsSubsequence(
            newArrayList(ONE, FOUR)); // futures are executed after runUntilDone finishes
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, FOUR));
  }

  @Test
  public void runUntilDoneWithBlockingPhaseTest() {
    // given
    final CompletableActorFuture<Void> future = CompletableActorFuture.completed(null);
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(runnable(TWO));
            final AtomicInteger count = new AtomicInteger();
            actor.runUntilDone(
                () -> {
                  final int couter = count.incrementAndGet();
                  if (couter > 2) {
                    actor.done();
                  } else {
                    actor.runOnCompletionBlockingCurrentPhase(future, futureConsumer(FOUR));
                  }

                  actions.add(ONE);
                });
            actor.run(runnable(THREE));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(ONE, ONE, ONE));
    assertThat(actor.actions).containsSequence(newArrayList(FOUR, FOUR));
    assertThat(actor.actions)
        .containsSubsequence(
            newArrayList(ONE, FOUR)); // futures are executed after runUntilDone finishes
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(THREE, FOUR));
  }

  @Test
  public void consumerTest() {
    // given
    final ConcurrentQueueChannel<Object> ch =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    ch.add(new Object());
    ch.add(new Object());

    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.consume(
                      ch,
                      () -> {
                        ch.poll();
                        actions.add(THREE);
                        actor.run(
                            runnable(
                                FOUR)); // this is done before the consumer fired for the second
                        // time
                      });
                  actions.add(ONE);
                });
            actor.run(runnable(TWO));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(THREE, FOUR, THREE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, THREE));
  }

  @Test
  public void conditionTest() {
    // given
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  final ActorCondition condition =
                      actor.onCondition(
                          "cond",
                          () -> {
                            actions.add(THREE);
                            actor.run(
                                runnable(
                                    FOUR)); // this is done before the condition is fired for the
                            // second time
                          });
                  conditionFuture.complete(condition);
                  actions.add(ONE);
                });
            actor.run(runnable(TWO));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    final ActorCondition condition = conditionFuture.join();
    condition.signal();
    condition.signal(); // condition is exactly once
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(THREE, FOUR, THREE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, THREE));
  }

  @Test
  public void timerTest() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.runAtFixedRate(
                      Duration.ofMillis(10),
                      () -> {
                        actions.add(THREE);
                        actor.run(
                            runnable(
                                FOUR)); // this is done before the timer is fired for the second
                        // time
                      });
                  actions.add(ONE);
                });
            actor.run(runnable(TWO));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(THREE, FOUR, THREE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, THREE));
  }

  @Test
  public void callTest() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.runOnCompletion(
                      future,
                      (v, t) -> {
                        actions.add(THREE);
                        actor.run(runnable(FOUR));
                      });
                  actions.add(ONE);
                });
            actor.run(runnable(TWO));
          }
        };

    // when
    schedulerRule.submitActor(actor);

    actor
        .actorControl()
        .call(
            () -> {
              actor.actions.add(FIVE);
            });

    future.complete(null);

    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(THREE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, FIVE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, FIVE));
  }

  @Test
  public void callWithBlockingPhaseTest() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final ActionRecordingActor actor =
        new ActionRecordingActor() {
          @Override
          protected void onActorStarted() {
            actor.run(
                () -> {
                  actor.runOnCompletionBlockingCurrentPhase(
                      future,
                      (v, t) -> {
                        actions.add(THREE);
                        actor.run(runnable(FOUR));
                      });
                  actions.add(ONE);
                });
            actor.run(runnable(TWO));
          }
        };

    // when
    schedulerRule.submitActor(actor);

    actor
        .actorControl()
        .call(
            () -> {
              actor.actions.add(FIVE);
            });

    future.complete(null);

    schedulerRule.workUntilDone();

    // then
    assertThat(actor.actions).containsSequence(newArrayList(THREE, FOUR));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, THREE));
    assertThat(actor.actions).containsSubsequence(newArrayList(ONE, FIVE));
    assertThat(actor.actions).containsSubsequence(newArrayList(TWO, FIVE));
  }
}
