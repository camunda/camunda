/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static org.agrona.UnsafeAccess.UNSAFE;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.FutureUtil;
import io.camunda.zeebe.util.Loggers;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Completable future implementation that is garbage free and reusable */
@SuppressWarnings("restriction")
public final class CompletableActorFuture<V> implements ActorFuture<V> {
  private static final long STATE_OFFSET;

  private static final int AWAITING_RESULT = 1;
  private static final int COMPLETING = 2;
  private static final int COMPLETED = 3;
  private static final int COMPLETED_EXCEPTIONALLY = 4;
  private static final int CLOSED = 5;

  static {
    try {
      STATE_OFFSET =
          UNSAFE.objectFieldOffset(CompletableActorFuture.class.getDeclaredField("state"));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private long completedAt;
  private V value;
  private String failure;
  private Throwable failureCause;
  private final ManyToOneConcurrentLinkedQueue<BiConsumer<V, Throwable>> blockedCallbacks =
      new ManyToOneConcurrentLinkedQueue<>();

  private final ReentrantLock completionLock = new ReentrantLock();
  private volatile int state = CLOSED;
  private Condition isDoneCondition;

  public CompletableActorFuture() {
    setAwaitingResult();
  }

  private CompletableActorFuture(final V value) {
    this.value = value;
    state = COMPLETED;
  }

  private CompletableActorFuture(final Throwable throwable) {
    ensureValidThrowable(throwable);
    failure = throwable.getMessage();
    failureCause = throwable;
    state = COMPLETED_EXCEPTIONALLY;
  }

  private void ensureValidThrowable(final Throwable throwable) {
    if (throwable == null) {
      throw new NullPointerException("Throwable must not be null.");
    }
  }

  public void setAwaitingResult() {
    state = AWAITING_RESULT;
    isDoneCondition = completionLock.newCondition();
  }

  public static <V> CompletableActorFuture<V> completed(final V result) {
    return new CompletableActorFuture<>(result); // cast for null result
  }

  public static <V> CompletableActorFuture<V> completedExceptionally(final Throwable throwable) {
    return new CompletableActorFuture<>(throwable);
  }

  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    final int state = this.state;
    return state == COMPLETED || state == COMPLETED_EXCEPTIONALLY;
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    try {
      return get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(final long timeout, final TimeUnit unit)
      throws ExecutionException, TimeoutException, InterruptedException {
    if (ActorThread.current() != null) {
      if (!isDone()) {
        throw new IllegalStateException(
            "Actor call get() on future which has not completed. "
                + "Actors must be non-blocking. Use actor.runOnCompletion().");
      }
    } else {
      // blocking get for non-actor threads
      completionLock.lock();
      try {
        long remaining = unit.toNanos(timeout);

        while (!isDone()) {
          if (remaining <= 0) {
            throw new TimeoutException("Timeout after: " + timeout + " " + unit);
          }
          remaining = isDoneCondition.awaitNanos(unit.toNanos(timeout));
        }
      } finally {
        completionLock.unlock();
      }
    }

    if (isCompletedExceptionally()) {
      throw new ExecutionException(failure, failureCause);
    } else {
      return value;
    }
  }

  public boolean isAwaitingResult() {
    return state == AWAITING_RESULT;
  }

  @Override
  public void complete(final V value) {
    if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
      this.value = value;
      state = COMPLETED;
      completedAt = System.nanoTime();
      notifyAllBlocked();
    } else {
      final String err =
          "Cannot complete future, the future is already completed "
              + (state == COMPLETED_EXCEPTIONALLY
                  ? ("exceptionally with " + failure + " ")
                  : " with value " + value);

      throw new IllegalStateException(err);
    }
  }

  @Override
  public void completeExceptionally(final String failure, final Throwable throwable) {
    // important for other actors that consume this by #runOnCompletion
    ensureValidThrowable(throwable);

    if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
      this.failure = failure;
      failureCause = throwable;
      state = COMPLETED_EXCEPTIONALLY;
      notifyAllBlocked();
    } else {
      final String err =
          "Cannot complete future, the future is already completed "
              + (state == COMPLETED_EXCEPTIONALLY
                  ? ("exceptionally with '" + failure + "' ")
                  : " with value " + value);
      throw new IllegalStateException(err, throwable);
    }
  }

  @Override
  public void completeExceptionally(final Throwable throwable) {
    ensureValidThrowable(throwable);
    completeExceptionally(throwable.getMessage(), throwable);
  }

  @Override
  public V join() {
    return FutureUtil.join(this);
  }

  @Override
  public V join(final long timeout, final TimeUnit timeUnit) {
    return FutureUtil.join(this, timeout, timeUnit);
  }

  @Override
  public void block(final ActorTask onCompletion) {
    blockedCallbacks.add((resIgnore, errorIgnore) -> onCompletion.tryWakeup());
  }

  @Override
  public void onComplete(final BiConsumer<V, Throwable> consumer) {
    if (ActorThread.isCalledFromActorThread()) {
      final ActorControl actorControl = ActorControl.current();
      actorControl.runOnCompletion(this, consumer);
    } else {
      // We don't reject this because, this is useful for tests. But the warning is a reminder not
      // to use this in production code.
      Loggers.ACTOR_LOGGER.warn(
          "No executor provided for ActorFuture#onComplete callback."
              + " This could block the actor that completes the future."
              + " Use onComplete(consumer, executor) instead.");
      onComplete(consumer, Runnable::run);
    }
  }

  @Override
  public void onComplete(final BiConsumer<V, Throwable> consumer, final Executor executor) {
    // There is a possible race condition that the future is completed before adding the consumer to
    // blockedCallBacks. Then the consumer will never get executed. To ensure that the
    // consumer is executed we check if the future is done, and trigger the consumer. However, if
    // future is completed after adding the consumer to the blockedCallBacks, but before the next
    // isDone is called the consumer might be triggered twice. To ensure exactly once execution, we
    // use the AtomicBoolean executedOnce. Since this method is not usually called from any actor,
    // this extra overhead would be acceptable.

    final AtomicBoolean executedOnce = new AtomicBoolean(false);
    final BiConsumer<V, Throwable> checkedConsumer =
        (res, error) ->
            executor.execute(
                () -> {
                  if (executedOnce.compareAndSet(false, true)) {
                    consumer.accept(res, error);
                  }
                });

    if (!isDone()) {
      // If future is already completed, blockedCallbacks are not notified again. So there is no
      // need to add the consumer.
      blockedCallbacks.add(checkedConsumer);
    }

    // Do not replace the following if(isDone()) with an else. The future might be completed after
    // the previous isDone() check.
    if (isDone()) {
      // Due to happens-before order guarantee between write to volatile field state and
      // non-volatile fields value and failureCause, we can read value and failureCause without
      // locks.
      checkedConsumer.accept(value, failureCause);
    }
  }

  @Override
  public boolean isCompletedExceptionally() {
    return state == COMPLETED_EXCEPTIONALLY;
  }

  @Override
  public Throwable getException() {
    if (!isCompletedExceptionally()) {
      throw new IllegalStateException(
          "Cannot call getException(); future is not completed exceptionally.");
    }

    return failureCause;
  }

  @Override
  public <U> ActorFuture<U> andThen(final Supplier<ActorFuture<U>> next, final Executor executor) {
    return andThen(
        ignored -> {
          try {
            return next.get();
          } catch (final Exception e) {
            return CompletableActorFuture.completedExceptionally(e);
          }
        },
        executor);
  }

  @Override
  public <U> ActorFuture<U> andThen(
      final Function<V, ActorFuture<U>> next, final Executor executor) {
<<<<<<< HEAD
    final ActorFuture<U> nextFuture = new CompletableActorFuture<>();
    onComplete(
        (thisResult, thisError) -> {
          if (thisError != null) {
            nextFuture.completeExceptionally(thisError);
          } else {
            next.apply(thisResult)
                .onComplete(
                    (nextResult, nextError) -> {
                      if (nextError != null) {
                        nextFuture.completeExceptionally(nextError);
                      } else {
                        nextFuture.complete(nextResult);
                      }
                    },
                    executor);
=======
    return andThen(
        (v, err) -> {
          if (err != null) {
            return CompletableActorFuture.completedExceptionally(err);
          } else {
            try {
              return next.apply(v);
            } catch (final Exception e) {
              return CompletableActorFuture.completedExceptionally(e);
            }
          }
        },
        executor);
  }

  @Override
  public <U> ActorFuture<U> andThen(
      final BiFunction<V, Throwable, ActorFuture<U>> next, final Executor executor) {
    final ActorFuture<U> nextFuture = new CompletableActorFuture<>();
    onComplete(
        (thisResult, thisError) -> {
          try {
            final var future = next.apply(thisResult, thisError);
            future.onComplete(nextFuture, executor);
          } catch (final Exception e) {
            nextFuture.completeExceptionally(e);
>>>>>>> 5be725b3 (fix: `andThen` completes exceptionally when `next` throws)
          }
        },
        executor);
    return nextFuture;
  }

  @Override
  public <U> ActorFuture<U> thenApply(final Function<V, U> next, final Executor executor) {
    final ActorFuture<U> nextFuture = new CompletableActorFuture<>();
    onComplete(
        (value, error) -> {
          if (error != null) {
            nextFuture.completeExceptionally(error);
            return;
          }

          try {
            nextFuture.complete(next.apply(value));
          } catch (final Exception e) {
            nextFuture.completeExceptionally(new CompletionException(e));
          }
        },
        executor);
    return nextFuture;
  }

  private void notifyAllBlocked() {
    notifyBlockedCallBacks();

    try {
      completionLock.lock();
      if (isDoneCondition != null) {
        // condition is null if the future was created with `completed` or `completedExceptionally`,
        // i.e. the future was never waiting for a result.
        isDoneCondition.signalAll();
      }
    } finally {
      completionLock.unlock();
    }
  }

  private void notifyBlockedCallBacks() {
    while (!blockedCallbacks.isEmpty()) {
      final var callBack = blockedCallbacks.poll();
      if (callBack != null) {
        callBack.accept(value, failureCause);
      }
    }
  }

  /** future is reusable after close */
  public boolean close() {
    final int prevState = UNSAFE.getAndSetInt(this, STATE_OFFSET, CLOSED);

    if (prevState != CLOSED) {
      value = null;
      failure = null;
      failureCause = null;
      notifyAllBlocked();
    }

    return prevState != CLOSED;
  }

  public boolean isClosed() {
    return state == CLOSED;
  }

  public void completeWith(final CompletableActorFuture<V> otherFuture) {
    if (!otherFuture.isDone()) {
      throw new IllegalArgumentException(
          "Future is not completed, can't complete this future with uncompleted future.");
    }

    if (otherFuture.isCompletedExceptionally()) {
      completeExceptionally(otherFuture.failureCause);
    } else {
      complete(otherFuture.value);
    }
  }

  @Override
  public String toString() {
    return "CompletableActorFuture{"
        + (isDone()
            ? (state == COMPLETED ? "value= " + value : "failure= " + failureCause)
            : " not completed (state " + state + ")")
        + "}";
  }

  public long getCompletedAt() {
    return completedAt;
  }
}
