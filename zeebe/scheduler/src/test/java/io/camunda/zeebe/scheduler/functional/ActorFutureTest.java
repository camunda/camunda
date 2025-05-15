/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.Rule;
import org.junit.Test;

public final class ActorFutureTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldInvokeCallbackOnFutureCompletion() {
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
  public void shouldInvokeCallbackOnBlockPhaseForFutureCompletion() {
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
  public void shouldInvokeCallbackOnAllFutureCompletedSuccessfully() {
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
  public void shouldInvokeCallbackOnEmptyFutureList() {
    // given
    final List<ActorFuture<Void>> futures = Collections.emptyList();

    final List<Throwable> invocations = new ArrayList<>();

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(
                futures,
                t -> {
                  invocations.add(t);
                });
          }
        };

    schedulerRule.submitActor(waitingActor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations).hasSize(1).containsNull();
  }

  @Test
  public void shouldInvokeCallbackOnAllFutureCompletedExceptionally() {
    // given
    final CompletableActorFuture<String> future1 = new CompletableActorFuture<>();
    final CompletableActorFuture<String> future2 = new CompletableActorFuture<>();

    final List<Throwable> invocations = new ArrayList<>();

    final Actor waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runOnCompletion(Arrays.asList(future1, future2), t -> invocations.add(t));
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
    assertThat(invocations.get(0).getMessage()).isEqualTo("bar");
  }

  @Test
  public void shouldNotBlockExecutionWhenRegisteredOnFuture() {
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
  public void shouldNotBlockExecutionOnRunOnCompletion() {
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
  public void shouldInvokeCallbackOnCompletedFuture() {
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
  public void shouldInvokeCallbackOnBlockPhaseForCompletedFuture() {
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
  public void shouldReturnCompletedFutureWithNullValue() {
    // given

    // when
    final CompletableActorFuture<Void> completed = CompletableActorFuture.completed(null);

    // then
    assertThat(completed).isDone();
    assertThat(completed.join()).isNull();
  }

  @Test
  public void shouldReturnCompletedFuture() {
    // given
    final Object result = new Object();

    // when
    final CompletableActorFuture<Object> completed = CompletableActorFuture.completed(result);

    // then
    assertThat(completed).isDone();
    assertThat(completed.join()).isEqualTo(result);
  }

  @Test
  public void shouldReturnCompletedExceptionallyFuture() {
    // given
    final RuntimeException result = new RuntimeException("Something bad happend!");

    // when
    final CompletableActorFuture<Object> completed =
        CompletableActorFuture.completedExceptionally(result);

    // then
    assertThat(completed).isDone();
    assertThat(completed.isCompletedExceptionally()).isTrue();

    assertThatThrownBy(() -> completed.join()).hasMessageContaining("Something bad happend!");
  }

  @Test
  public void shouldInvokeCallbacksAfterCloseIsCalled() {
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
  public void joinShouldThrowExecutionException() {
    // given
    final CompletableActorFuture<Object> future = new CompletableActorFuture<>();
    final RuntimeException throwable = new RuntimeException();

    // when
    future.completeExceptionally(throwable);

    // then
    final AbstractThrowableAssert<?, ? extends Throwable> thrownBy =
        assertThatThrownBy(() -> future.join());
    thrownBy.isInstanceOf(ExecutionException.class);
    thrownBy.hasCause(throwable);
  }

  @Test
  public void shouldCompleteFutureAndWaitOnNonActorThread() throws Exception {
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
  public void shouldCompleteFutureExceptionallyAndWaitOnNonActorThread() {
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
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("moep");
  }

  @Test
  public void shouldReturnValueOnNonActorThread() throws Exception {
    // given
    final CompletableActorFuture<String> future = CompletableActorFuture.completed("value");

    // when
    final String value = future.get(5, TimeUnit.MILLISECONDS);

    // then
    assertThat(value).isEqualTo("value");
  }

  @Test
  public void shouldThrowExceptionOnNonActorThread() {
    // given
    final CompletableActorFuture<String> future =
        CompletableActorFuture.completedExceptionally(new IllegalArgumentException("moep"));

    // expect
    assertThatThrownBy(() -> future.get(5, TimeUnit.MILLISECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasMessage("moep");
  }

  @Test
  public void shouldThrowTimeoutOnNonActorThread() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    // expect
    assertThatThrownBy(() -> future.get(5, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class)
        .hasMessage("Timeout after: 5 MILLISECONDS");
  }

  @Test
  public void shouldFailToStaticallyCreateExceptionallyCompletedFutureWithNull() {
    // when
    final RuntimeException result = null;

    // then
    assertThatThrownBy(() -> CompletableActorFuture.completedExceptionally(result))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Throwable must not be null.");
  }

  @Test
  public void shouldFailToExceptionallyCompleteFutureWithNull() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final RuntimeException result = null;

    // then/then
    assertThatThrownBy(() -> future.completeExceptionally(result))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Throwable must not be null.");
  }

  @Test
  public void shouldFailToExceptionallyCompleteFutureWithNullAndMessage() {
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
  public void shouldRunOnComplete() {
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
  public void shouldInvokeCallbackOnFutureCompletionIfCallerIsNotActor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicInteger callbackInvocations = new AtomicInteger(0);

    future.onComplete((r, t) -> callbackInvocations.incrementAndGet());
    future.onSuccess((r) -> callbackInvocations.incrementAndGet());

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
    assertThat(callbackInvocations).hasValue(2);
  }

  @Test
  public void shouldInvokeCallbackOnFutureCompletionOnProvidedExecutor() {
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
    future.onSuccess((r) -> callbackInvocations.incrementAndGet(), decoratedExecutor);

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
    assertThat(callbackInvocations).hasValue(2);
    assertThat(executorCount).hasValue(2);
  }

  @Test
  public void shouldInvokeCallbackOnFutureCompletionExceptionIfCallerIsNotActor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> callBackError = new AtomicReference<>();
    final AtomicReference<Throwable> callBackErrorOnError = new AtomicReference<>();

    future.onComplete((r, t) -> callBackError.set(t));
    future.onError(callBackErrorOnError::set);

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
    assertThat(callBackErrorOnError.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
  }

  @Test
  public void shouldInvokeCallbackOnFutureCompletionErrorOnProvidedExecutor() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> callBackError = new AtomicReference<>();
    final AtomicReference<Throwable> callBackErrorOnError = new AtomicReference<>();

    final AtomicInteger executorCount = new AtomicInteger(0);
    final Executor decoratedExecutor =
        runnable -> {
          executorCount.getAndIncrement();
          runnable.run();
        };

    future.onComplete((r, t) -> callBackError.set(t), decoratedExecutor);
    future.onError(callBackErrorOnError::set, decoratedExecutor);

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
    assertThat(callBackErrorOnError.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
    assertThat(executorCount).hasValue(2);
  }

  @Test
  public void shouldInvokeCallbackAddedAfterCompletionIfCallerIsNonActor() {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
    final AtomicInteger futureResult = new AtomicInteger();
    final AtomicInteger futureResultOnSuccess = new AtomicInteger();

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
    future.onSuccess(futureResultOnSuccess::set, decoratedExecutor);

    // then
    assertThat(futureResult.get()).isOne();
    assertThat(futureResultOnSuccess.get()).isOne();
    assertThat(executorCount).hasValue(2);
  }

  @Test
  public void shouldInvokeCallbackAddedAfterCompletionErrorIfCallerIsNonActor() {
    // given
    final CompletableActorFuture<Integer> future = new CompletableActorFuture<>();
    final AtomicReference<Throwable> futureResult = new AtomicReference<>();
    final AtomicReference<Throwable> futureResultOnError = new AtomicReference<>();

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
    future.onError(futureResultOnError::set, decoratedExecutor);

    // then
    assertThat(futureResult.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
    assertThat(futureResultOnError.get())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Expected");
    assertThat(executorCount).hasValue(2);
  }

  @Test
  public void shouldChainWithAndThen() {
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
  public void andThenChainPropagatesInitialException() {
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
  public void andThenChainPropagatesValue() {
    // given
    final var chained =
        CompletableActorFuture.completed("expected")
            .andThen(CompletableActorFuture::completed, Runnable::run);

    // then
    assertThat(chained).succeedsWithin(Duration.ofSeconds(1)).isEqualTo("expected");
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
