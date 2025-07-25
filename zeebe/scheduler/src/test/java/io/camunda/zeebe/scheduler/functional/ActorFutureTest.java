/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ActorFutureTest {
  @RegisterExtension
  final ControlledActorSchedulerExtension schedulerRule = new ControlledActorSchedulerExtension();

  @Test
  void shouldInvokeCallbackOnFutureCompletion() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicInteger callbackInvocations = new AtomicInteger(0);

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(
                future,
                (r, t) -> {
                  callbackInvocations.incrementAndGet();
                });
          }
        };

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(null);
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callbackInvocations).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackOnBlockPhaseForFutureCompletion() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicInteger callbackInvocations = new AtomicInteger(0);

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletionBlockingCurrentPhase(
                future, (r, t) -> callbackInvocations.incrementAndGet());
          }
        };

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(null);
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callbackInvocations).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackOnAllFutureCompletedSuccessfully() {
    // given
    final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
    final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

    final List<Throwable> invocations = new ArrayList<>();
    final List<String> results = new ArrayList<>();

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(
                Arrays.asList(future1, future2),
                t -> {
                  invocations.add(t);

                  results.add(future1.join());
                  results.add(future2.join());
                });
          }
        };

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future1.complete("foo");
            future2.complete("bar");
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations).hasSize(1).containsNull();
    assertThat(results).contains("foo", "bar");
  }

  @Test
  void shouldInvokeCallbackOnEmptyFutureList() {
    // given
    final List<ActorFuture<Void>> futures = Collections.emptyList();

    final List<Throwable> invocations = new ArrayList<>();

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(futures, invocations::add);
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations).hasSize(1).containsNull();
  }

  @Test
  void shouldInvokeCallbackOnAllFutureCompletedExceptionally() {
    // given
    final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
    final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

    final List<Throwable> invocations = new ArrayList<>();

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(Arrays.asList(future1, future2), invocations::add);
          }
        };

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future1.completeExceptionally(new RuntimeException("foo"));
            future2.completeExceptionally(new RuntimeException("bar"));
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations).hasSize(1);
    assertThat(invocations.getFirst().getMessage()).isEqualTo("bar");
  }

  @Test
  void shouldNotBlockExecutionWhenRegisteredOnFuture() {
    // given
    final BlockedCallActor actor = new BlockedCallActor();
    schedulerRule.submitActor(actor);
    actor.waitOnFuture(); // actor is waiting on future
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Integer> future = actor.call(42);
    schedulerRule.workUntilDone();
    final Integer result = future.join();

    // then
    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldNotBlockExecutionOnRunOnCompletion() {
    // given
    final BlockedCallActorWithRunOnCompletion actor = new BlockedCallActorWithRunOnCompletion();
    schedulerRule.submitActor(actor);
    actor.waitOnFuture(); // actor is waiting on future
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Integer> future = actor.call(42);
    schedulerRule.workUntilDone();
    final Integer result = future.join();

    // then
    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldInvokeCallbackOnCompletedFuture() {
    // given
    final AtomicReference<String> futureResult = new AtomicReference<>();

    schedulerRule.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(
                CompletableActorFuture.completed("foo"), (r, t) -> futureResult.set(r));
          }
        });

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(futureResult.get()).isEqualTo("foo");
  }

  @Test
  void shouldInvokeCallbackOnBlockPhaseForCompletedFuture() {
    // given
    final AtomicReference<String> futureResult = new AtomicReference<>();

    schedulerRule.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletionBlockingCurrentPhase(
                CompletableActorFuture.completed("foo"), (r, t) -> futureResult.set(r));
          }
        });

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(futureResult.get()).isEqualTo("foo");
  }

  @Test
  void shouldReturnCompletedFutureWithNullValue() {
    // given

    // when
    final CompletableActorFuture<Void> completed = CompletableActorFuture.completed(null);

    // then
    assertThat(completed).isDone();
    assertThat(completed.join()).isNull();
  }

  @Test
  void shouldReturnCompletedFuture() {
    // given
    final Object result = new Object();

    // when
    final CompletableActorFuture<Object> completed = CompletableActorFuture.completed(result);

    // then
    assertThat(completed).isDone();
    assertThat(completed.join()).isEqualTo(result);
  }

  @Test
  void shouldReturnCompletedExceptionallyFuture() {
    // given
    final RuntimeException result = new RuntimeException("Something bad happened!");

    // when
    final CompletableActorFuture<Object> completed =
        CompletableActorFuture.completedExceptionally(result);

    // then
    assertThat(completed).isDone();
    assertThat(completed.isCompletedExceptionally()).isTrue();

    assertThatThrownBy(completed::join).hasMessageContaining("Something bad happened!");
  }

  @Test
  void shouldInvokeCallbacksAfterCloseIsCalled() {
    // given
    final CompletableActorFuture<Object> f1 = new CompletableActorFuture<>();
    final CompletableActorFuture<Object> f2 = new CompletableActorFuture<>();

    final Object result1 = new Object();
    final Object result2 = new Object();

    final TestActor actor = new TestActor();

    schedulerRule.submitActor(actor);

    final List<Object> receivedObjects = new ArrayList<>();

    actor.awaitFuture(f1, (o, t) -> receivedObjects.add(o));
    actor.awaitFuture(f2, (o, t) -> receivedObjects.add(o));
    schedulerRule.workUntilDone();

    // when
    /*
     * Explanation:
     *   - #close submits the close job
     *   - #workUntilDone picks up the close job and before execution polls the future subscriptions,
     *     therefore appends the subscription callback jobs (callback1, callback2) to the close job
     *     => job queue: close => callback1 => callback2
     *   - the close job detaches the jobs again, but leaves the other jobs connected
     *     => job queue: close; detached: callback1 => callback2
     *   - after the close job finishes, the subscriptions are polled again, so the callback jobs are submitted again
     *     => job queue: callback1 => callback2 => callback2
     *   - callback2 is now connected to itself and is therefore executed twice in succession
     */
    f1.complete(result1);
    f2.complete(result2);
    actor.close();
    schedulerRule.workUntilDone();

    // then
    assertThat(receivedObjects).containsExactly(result1, result2);
  }

  @Test
  void joinShouldThrowExecutionException() {
    // given
    final CompletableActorFuture<Object> future = new CompletableActorFuture<>();
    final RuntimeException throwable = new RuntimeException();

    // when
    future.completeExceptionally(throwable);

    // then
    final AbstractThrowableAssert<?, ? extends Throwable> thrownBy =
        assertThatThrownBy(future::join);
    thrownBy.isInstanceOf(ExecutionException.class);
    thrownBy.hasCause(throwable);
  }

  @Test
  void shouldCompleteFutureAndWaitOnNonActorThread() throws Exception {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();

    // when
    schedulerRule.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(0xFA);
          }
        });

    new Thread() {
      @Override
      public void run() {
        schedulerRule.workUntilDone();
      }
    }.start();

    final Integer value = future.get();

    // then
    assertThat(value).isEqualTo(0xFA);
  }

  @Test
  void shouldCompleteFutureExceptionallyAndWaitOnNonActorThread() {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();

    // when
    schedulerRule.submitActor(
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.completeExceptionally(new IllegalArgumentException("moep"));
          }
        });

    new Thread() {
      @Override
      public void run() {
        schedulerRule.workUntilDone();
      }
    }.start();

    // expect
    assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class).hasMessage("moep");
  }

  @Test
  void shouldReturnValueOnNonActorThread() throws Exception {
    // given
    final CompletableActorFuture<String> future = CompletableActorFuture.completed("value");

    // when
    final String value = future.get(5, TimeUnit.MILLISECONDS);

    // then
    assertThat(value).isEqualTo("value");
  }

  @Test
  void shouldThrowExceptionOnNonActorThread() {
    // given
    final CompletableActorFuture<String> future =
        CompletableActorFuture.completedExceptionally(new IllegalArgumentException("moep"));

    // expect
    assertThatThrownBy(() -> future.get(5, TimeUnit.MILLISECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasMessage("moep");
  }

  @Test
  void shouldThrowTimeoutOnNonActorThread() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    // expect
    assertThatThrownBy(() -> future.get(5, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class)
        .hasMessage("Timeout after: 5 MILLISECONDS");
  }

  @Test
  void shouldFailToStaticallyCreateExceptionallyCompletedFutureWithNull() {
    // when
    final RuntimeException result = null;

    // then
    assertThatThrownBy(() -> CompletableActorFuture.completedExceptionally(result))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Throwable must not be null.");
  }

  @Test
  void shouldFailToExceptionallyCompleteFutureWithNull() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final RuntimeException result = null;

    // then/then
    assertThatThrownBy(() -> future.completeExceptionally(result))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Throwable must not be null.");
  }

  @Test
  void shouldFailToExceptionallyCompleteFutureWithNullAndMessage() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final RuntimeException result = null;
    final String message = "foo";

    // then/then
    assertThatThrownBy(() -> future.completeExceptionally(message, result))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Throwable must not be null.");
  }

  @Test
  void shouldRunOnComplete() {
    // given
    final ActorB actorB = new ActorB();
    schedulerRule.submitActor(actorB);
    final ActorA actorA = new ActorA(actorB);
    schedulerRule.submitActor(actorA);

    // when
    final ActorFuture<Integer> future = actorA.sumValues();
    schedulerRule.workUntilDone();

    // then
    assertThat(future.isDone()).isTrue();
    assertThat(future.join()).isEqualTo(0xCAFF);
  }

  @Test
  void shouldInvokeCallbackOnFutureCompletionIfCallerIsNotActor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicInteger callbackInvocations = new AtomicInteger(0);

    future.onComplete((r, t) -> callbackInvocations.incrementAndGet());

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(null);
          }
        };

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callbackInvocations).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackOnFutureCompletionOnProvidedExecutor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicInteger callbackInvocations = new AtomicInteger(0);

    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    future.onComplete((r, t) -> callbackInvocations.incrementAndGet(), decoratedExecutor);

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(null);
          }
        };

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callbackInvocations).hasValue(1);
    assertThat(executorCount).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackOnFutureCompletionExceptionIfCallerIsNotActor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> callBackError = new AtomicReference<>();

    future.onComplete((r, t) -> callBackError.set(t));

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.completeExceptionally(new RuntimeException("Expected"));
          }
        };

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callBackError.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
  }

  @Test
  void shouldInvokeCallbackOnFutureCompletionErrorOnProvidedExecutor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> callBackError = new AtomicReference<>();

    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    future.onComplete((r, t) -> callBackError.set(t), decoratedExecutor);

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.completeExceptionally(new RuntimeException("Expected"));
          }
        };

    // when
    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(callBackError.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
    assertThat(executorCount).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackAddedAfterCompletionIfCallerIsNonActor() {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
    final AtomicInteger futureResult = new AtomicInteger();

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.complete(1);
          }
        };

    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // when
    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    future.onComplete((r, t) -> futureResult.set(r), decoratedExecutor);

    // then
    assertThat(futureResult.get()).isOne();
    assertThat(executorCount).hasValue(1);
  }

  @Test
  void shouldInvokeCallbackAddedAfterCompletionErrorIfCallerIsNonActor() {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> futureResult = new AtomicReference<>();

    final Actor completingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            future.completeExceptionally(new RuntimeException("Expected"));
          }
        };

    schedulerRule.submitActor(completingActor);
    schedulerRule.workUntilDone();

    // when

    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    future.onComplete((r, t) -> futureResult.set(t), decoratedExecutor);

    // then
    assertThat(futureResult.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
    assertThat(executorCount).hasValue(1);
  }

  @Test
  void shouldChainWithAndThen() {
    // given
    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    final var future1 = new CompletableActorFuture<>();
    final var future2 = new CompletableActorFuture<>();

    // when -- chain on uncompleted future
    final var chainedFuture = future1.andThen(r -> future2, decoratedExecutor);

    // then -- nothing ran yet
    assertThat(chainedFuture).isNotDone();
    assertThat(executorCount).hasValue(0);

    // when -- complete initial future
    future1.complete(null);

    // then -- chained future is still not completed
    assertThat(chainedFuture).isNotDone();
    assertThat(executorCount).hasValue(1);

    // when -- complete next future
    future2.complete(null);

    // then -- chained future is completed
    assertThat(chainedFuture).isDone();
    assertThat(executorCount).hasValue(2);
  }

  @Test
  void andThenChainPropagatesInitialException() {
    // given
    final var future1 = new CompletableActorFuture<>();
    final var future2 = new CompletableActorFuture<>();
    final var chained = future1.andThen(r -> future2, Runnable::run);
    final var expectedException = new RuntimeException("expected");

    // when
    future1.completeExceptionally(expectedException);

    // then
    assertThat(chained)
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableThat()
        .withCause(expectedException);
  }

  @Test
  void andThenChainPropagatesValue() {
    // given
    final var chained =
        CompletableActorFuture.completed("expected")
            .andThen(CompletableActorFuture::completed, Runnable::run);

    // then
    assertThat(chained).succeedsWithin(Duration.ofSeconds(1)).isEqualTo("expected");
  }

  @Test
<<<<<<< HEAD
=======
  void andThenBiFunctionPropagatesValue() {
    final var chained =
        CompletableActorFuture.completed("expected")
            .andThen((v, err) -> CompletableActorFuture.completed(v), Runnable::run);
    assertThat(chained).isDone();
    assertThat(chained).succeedsWithin(Duration.ZERO).isEqualTo("expected");
  }

  @Test
  void andThenBiFunctionPropagatesError() {
    final var chained =
        CompletableActorFuture.completedExceptionally(new RuntimeException("expected"))
            .andThen((v, err) -> CompletableActorFuture.completedExceptionally(err), Runnable::run);
    assertThat(chained).isDone();
    assertThat(chained).failsWithin(Duration.ZERO).withThrowableThat().withMessage("expected");
  }

  @Test
  void andThenCanAbsolveAFailedFuture() {
    // given
    final var chained =
        CompletableActorFuture.completedExceptionally(new RuntimeException(""))
            .andThen((v, throwable) -> CompletableActorFuture.completed(1), Runnable::run);
    assertThat(chained).isDone();
    assertThat(chained).succeedsWithin(Duration.ZERO).isEqualTo(1);
  }

  @Test
  void andThenSupplierShouldCompleteExceptionallyOnException() {
    // given
    final var expectedException = new RuntimeException("Supplier exception");
    final var chained =
        CompletableActorFuture.completed("input")
            .andThen(
                () -> {
                  throw expectedException;
                },
                Runnable::run);

    // then
    assertThat(chained)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCause(expectedException);
  }

  @Test
  void andThenFunctionShouldCompleteExceptionallyOnException() {
    // given
    final var expectedException = new RuntimeException("Function exception");
    final var chained =
        CompletableActorFuture.completed("input")
            .andThen(
                input -> {
                  throw expectedException;
                },
                Runnable::run);

    // then
    assertThat(chained)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCause(expectedException);
  }

  @Test
  void andThenBiFunctionShouldCompleteExceptionallyOnException() {
    // given
    final var expectedException = new RuntimeException("BiFunction exception");
    final var chained =
        CompletableActorFuture.completed("input")
            .andThen(
                (value, error) -> {
                  throw expectedException;
                },
                Runnable::run);

    // then
    assertThat(chained)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCause(expectedException);
  }

  @Test
>>>>>>> 5be725b3 (fix: `andThen` completes exceptionally when `next` throws)
  void shouldChainThenApply() {
    // given
    final var future = new CompletableActorFuture<Integer>();
    final var chained = future.thenApply(value -> value + 1, Runnable::run);

    // when
    future.complete(1);

    // then
    assertThat(chained).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(2);
  }

  @Test
  void shouldShortCircuitThenApplyOnFailure() {
    // given
    final var future = new CompletableActorFuture<Integer>();
    final var called = new AtomicBoolean(false);
    final var failure = new RuntimeException("foo");
    final var chained =
        future.thenApply(
            value -> {
              called.set(true);
              return value;
            },
            Runnable::run);

    // when
    future.completeExceptionally(failure);

    // then
    assertThat(chained)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .havingCause()
        .isSameAs(failure);
    assertThat(called).isFalse();
  }

  @Test
  void shouldChainAndCompleteIntermediateFuturesOnApply() {
    // given
    final var original = new CompletableActorFuture<Integer>();
    final var firstElement = original.thenApply(value -> value + 1, Runnable::run);
    final var secondElement = firstElement.thenApply(value -> value + 1, Runnable::run);

    // when
    original.complete(1);

    // then
    assertThat(firstElement).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(2);
    assertThat(secondElement).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(3);
  }

  @Test
  void shouldApplyOnExecutor() {
    // given
    final var future = new CompletableActorFuture<Integer>();
    final var onExecutor = new AtomicBoolean(false);
    final var calledOnExecutor = new AtomicBoolean(false);
    final var chained =
        future.thenApply(
            value -> {
              calledOnExecutor.set(onExecutor.get());
              return value + 1;
            },
            task -> {
              onExecutor.set(true);
              task.run();
              onExecutor.set(false);
            });

    // when
    future.complete(1);

    // then
    assertThat(chained).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(2);
    assertThat(calledOnExecutor).isTrue();
  }

  @Test
  void shouldShortCircuitMiddleOfChainWithActor() {
    // given
    final var original = new CompletableActorFuture<Integer>();
    final var testActor = new TestActor();
    final var failure = new RuntimeException("foo");
    final AtomicReference<ActorFuture<Integer>> firstElement = new AtomicReference<>();
    final AtomicReference<ActorFuture<Integer>> secondElement = new AtomicReference<>();
    final AtomicReference<ActorFuture<Integer>> thirdElement = new AtomicReference<>();
    schedulerRule.submitActor(testActor);
    schedulerRule.workUntilDone();

    testActor.run(
        () -> {
          firstElement.set(original.thenApply(value -> value + 1, testActor));
          secondElement.set(
              firstElement
                  .get()
                  .thenApply(
                      value -> {
                        throw failure;
                      },
                      testActor));
          thirdElement.set(secondElement.get().thenApply(value -> value + 1, testActor));
        });
    schedulerRule.workUntilDone();

    // when
    original.complete(1);
    schedulerRule.workUntilDone();

    // then
    assertThat(original).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(1);
    assertThat(firstElement.get()).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(2);
    assertThat(secondElement.get())
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .isInstanceOf(ExecutionException.class)
        .havingRootCause()
        .isSameAs(failure);
    assertThat(thirdElement.get())
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .isInstanceOf(ExecutionException.class)
        .havingRootCause()
        .isSameAs(failure);
  }

  @Test
  void shouldShortCircuitMiddleOfChain() {
    // given
    final var original = new CompletableActorFuture<Integer>();
    final var failure = new RuntimeException("foo");
    final var firstElement = original.thenApply(value -> value + 1, Runnable::run);
    final var secondElement =
        firstElement.<Integer>thenApply(
            value -> {
              throw failure;
            },
            Runnable::run);
    final var thirdElement = secondElement.thenApply(value -> value + 1, Runnable::run);

    // when
    original.complete(1);

    // then
    assertThat(original).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(1);
    assertThat(firstElement).succeedsWithin(Duration.ofSeconds(1)).isEqualTo(2);
    assertThat(secondElement)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .isInstanceOf(ExecutionException.class)
        .havingRootCause()
        .isSameAs(failure);
    assertThat(thirdElement)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .isInstanceOf(ExecutionException.class)
        .havingRootCause()
        .isSameAs(failure);
  }

  private static final class BlockedCallActor extends Actor {
    public void waitOnFuture() {
      actor.call(
          () -> {
            actor.runOnCompletionBlockingCurrentPhase(
                new CompletableActorFuture<>(),
                (r, t) -> {
                  // never called since future is never completed
                });
          });
    }

    public ActorFuture<Integer> call(final int returnValue) {
      return actor.call(() -> returnValue);
    }
  }

  private static final class BlockedCallActorWithRunOnCompletion extends Actor {
    public void waitOnFuture() {
      actor.call(
          () -> {
            actor.runOnCompletion(
                new CompletableActorFuture<>(),
                (r, t) -> {
                  // never called since future is never completed
                });
          });
    }

    public ActorFuture<Integer> call(final int returnValue) {
      return actor.call(() -> returnValue);
    }
  }

  private static final class TestActor extends Actor {

    public <T> void awaitFuture(
        final ActorFuture<T> f, final BiConsumer<T, Throwable> onCompletion) {
      actor.call(() -> actor.runOnCompletionBlockingCurrentPhase(f, onCompletion));
    }

    @Override
    public void close() {
      actor.close();
    }
  }

  private static final class ActorA extends Actor {

    private final ActorB actorB;

    ActorA(final ActorB actorB) {
      this.actorB = actorB;
    }

    ActorFuture<Integer> sumValues() {

      final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
      actor.call(
          () -> {
            actorB
                .getValue()
                .onComplete(
                    (v, t) -> {
                      future.complete(v + 1);

                      final ActorThread current = ActorThread.current();
                      assert current != null : "Expected to run in actor thread!";
                      assert current.getCurrentTask().getActor() == this
                          : "Expected to run in same actor!";
                    });
          });

      return future;
    }
  }

  private static final class ActorB extends Actor {
    public ActorFuture<Integer> getValue() {
      return actor.call(() -> 0xCAFE);
    }
  }
}
