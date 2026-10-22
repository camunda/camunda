/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import static io.camunda.zeebe.util.Nulls.uncheckedCastToNonNull;
import static io.camunda.zeebe.util.Unit.unit;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.FutureUtil;
import io.camunda.zeebe.util.Loggers;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.agrona.UnsafeApi;
import org.jspecify.annotations.Nullable;

/** Completable future implementation that is garbage free and reusable */
@SuppressWarnings("restriction")
public final class CompletableActorFuture<V extends @Nullable Object> implements ActorFuture<V> {
  private static final long STATE_OFFSET;

  private static final VarHandle BLOCKED_CALLBACKS;

  private static final int AWAITING_RESULT = 1;
  private static final int COMPLETING = 2;
  private static final int COMPLETED = 3;
  private static final int COMPLETED_EXCEPTIONALLY = 4;
  private static final int CLOSED = 5;

  static {
    try {
      STATE_OFFSET =
          UnsafeApi.objectFieldOffset(CompletableActorFuture.class.getDeclaredField("state"));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }

    try {
      BLOCKED_CALLBACKS =
          MethodHandles.lookup()
              .findVarHandle(CompletableActorFuture.class, "blockedCallbacks", CallbackNode.class);
    } catch (final ReflectiveOperationException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private long completedAt;
  private @Nullable V value;
  private @Nullable String failure;
  private @Nullable Throwable failureCause;

  // Most futures never have a callback registered, so these are kept as a lock-free stack of nodes
  // that costs nothing until the first callback is added. See #notifyAllBlocked().
  private volatile @Nullable CallbackNode<V> blockedCallbacks;

  private volatile int state = CLOSED;

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

  private void ensureValidThrowable(final @Nullable Throwable throwable) {
    if (throwable == null) {
      throw new NullPointerException("Throwable must not be null.");
    }
  }

  public void setAwaitingResult() {
    state = AWAITING_RESULT;
  }

  public static CompletableActorFuture<Void> completed() {
    return CompletableActorFuture.completed(unit());
  }

  public static <V extends @Nullable Object> CompletableActorFuture<V> completed(final V result) {
    return new CompletableActorFuture<>(result); // cast for null result
  }

  public static <V extends @Nullable Object> CompletableActorFuture<V> completedExceptionally(
      final Throwable throwable) {
    return new CompletableActorFuture<>(throwable);
  }

  /**
   * Traverse the provided collection sequentially, applying the provided function making sure that
   * the futures are started sequentially: When one computation completes, the next starts after it.
   *
   * @param collection To traverse to use as input for the function
   * @param function The computation to execute for each element of the collection
   * @return a Void future that will complete successfully if all Futures started complete
   *     successfully. if one future returns an error, the computation is stopped (the rest of the
   *     Future will not be started) and the error is returned
   */
  public static <A> ActorFuture<Void> traverseIgnoring(
      final Collection<A> collection,
      final Function<A, ActorFuture<Void>> function,
      final Executor executor) {
    ActorFuture<Void> future = completed();
    for (final A a : collection) {
      future = future.andThen(unused -> function.apply(a), executor);
    }
    return future;
  }

  /**
   * Executes a supplier that provides an {@code ActorFuture} and catches any synchronous
   * exceptions, wrapping them in the returned future.
   *
   * @param <A> The type of the result provided by the {@code ActorFuture}.
   * @param supplier A supplier that provides an {@code ActorFuture}.
   * @return The {@code ActorFuture} provided by the supplier, or an exceptionally-completed {@code
   *     ActorFuture} if an exception was thrown by the supplier.
   */
  public static <A> ActorFuture<A> catching(final Supplier<ActorFuture<A>> supplier) {
    try {
      return supplier.get();
    } catch (final Throwable e) {
      return CompletableActorFuture.completedExceptionally(e);
    }
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
      final long deadline = System.nanoTime() + unit.toNanos(timeout);

      while (!isDone()) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new TimeoutException("Timeout after: " + timeout + " " + unit);
        }

        final var latch = new CountDownLatch(1);
        addBlockedCallback((ignoredValue, ignoredError) -> latch.countDown());
        // the future may have been completed before the callback was added, in which case it will
        // not be notified anymore
        if (!isDone()) {
          latch.await(remaining, TimeUnit.NANOSECONDS);
        }
      }
    }

    if (isCompletedExceptionally()) {
      throw new ExecutionException(failure, failureCause);
    } else {
      // value is set by complete() before this state is reachable
      return uncheckedCastToNonNull(value);
    }
  }

  public boolean isAwaitingResult() {
    return state == AWAITING_RESULT;
  }

  @Override
  public void complete(final V value) {
    if (UnsafeApi.compareAndSetInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
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
  public void completeExceptionally(final @Nullable String failure, final Throwable throwable) {
    // important for other actors that consume this by #runOnCompletion
    ensureValidThrowable(throwable);

    if (UnsafeApi.compareAndSetInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
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
    addBlockedCallback((resIgnore, errorIgnore) -> onCompletion.tryWakeup());
  }

  @Override
  public void onComplete(final BiConsumer<V, @Nullable Throwable> consumer) {
    if (ActorThread.isCalledFromActorThread()) {
      final ActorControl actorControl = ActorControl.current();
      actorControl.runOnCompletion(this, consumer);
    } else {
      // We don't reject this because, this is useful for tests. But the warning is a reminder not
      // to use this in production code.
      Loggers.ACTOR_LOGGER.warn(
          """
              [PotentiallyBlocking] No executor provided for ActorFuture#onComplete callback for {}.\
               This could block the actor that completes the future.\
               Use onComplete(consumer, executor) instead.""",
          consumer.getClass().getName());
      onComplete(consumer, Runnable::run);
    }
  }

  @Override
  public void onComplete(
      final BiConsumer<V, @Nullable Throwable> consumer, final Executor executor) {
    // There is a possible race condition that the future is completed before adding the consumer to
    // blockedCallBacks. Then the consumer will never get executed. To ensure that the
    // consumer is executed we check if the future is done, and trigger the consumer. However, if
    // future is completed after adding the consumer to the blockedCallBacks, but before the next
    // isDone is called the consumer might be triggered twice. To ensure exactly once execution, we
    // use the AtomicBoolean executedOnce. Since this method is not usually called from any actor,
    // this extra overhead would be acceptable.

    final AtomicBoolean executedOnce = new AtomicBoolean(false);
    final BiConsumer<V, @Nullable Throwable> checkedConsumer =
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
      addBlockedCallback(checkedConsumer);
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

    return uncheckedCastToNonNull(failureCause);
  }

  @Override
  public <U extends @Nullable Object> ActorFuture<U> andThen(
      final Supplier<ActorFuture<U>> next, final Executor executor) {
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
  public <U extends @Nullable Object> ActorFuture<U> andThen(
      final Function<V, ActorFuture<U>> next, final Executor executor) {
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
  public <U extends @Nullable Object> ActorFuture<U> andThen(
      final BiFunction<V, @Nullable Throwable, ActorFuture<U>> next, final Executor executor) {
    final ActorFuture<U> nextFuture = new CompletableActorFuture<>();
    onComplete(
        (thisResult, thisError) -> {
          try {
            final var future = next.apply(thisResult, thisError);
            future.onComplete(nextFuture, executor);
          } catch (final Exception e) {
            nextFuture.completeExceptionally(e);
          }
        },
        executor);
    return nextFuture;
  }

  @Override
  public <U extends @Nullable Object> ActorFuture<U> thenApply(
      final Function<V, U> next, final Executor executor) {
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

  @Override
  public <U extends @Nullable Object> ActorFuture<U> thenApply(final Function<V, U> next) {
    if (ActorThread.isCalledFromActorThread()) {
      final ActorControl actorControl = ActorControl.current();
      return thenApply(next, actorControl);
    } else {
      // We don't reject this because, this is useful for tests. But the warning is a reminder not
      // to use this in production code.
      Loggers.ACTOR_LOGGER.warn(
          """
              [PotentiallyBlocking] No executor provided for ActorFuture#thenApply method at {}.\
               This could block the actor that completes the future.\
               Use thenApply(consumer, executor) instead.""",
          next.getClass().getName());
      return thenApply(next, Runnable::run);
    }
  }

  private void addBlockedCallback(final BiConsumer<V, @Nullable Throwable> callback) {
    final var node = new CallbackNode<>(callback);
    CallbackNode<V> head;
    do {
      head = blockedCallbacks;
      node.next = head;
    } while (!BLOCKED_CALLBACKS.compareAndSet(this, head, node));
  }

  private void notifyAllBlocked() {
    // callbacks may be added by other callbacks, or concurrently by other threads
    while (true) {
      @SuppressWarnings("unchecked")
      var pending = (CallbackNode<V>) BLOCKED_CALLBACKS.getAndSet(this, (CallbackNode<V>) null);
      if (pending == null) {
        return;
      }

      // the stack is in reverse order of registration
      CallbackNode<V> inOrder = null;
      while (pending != null) {
        final var next = pending.next;
        pending.next = inOrder;
        inOrder = pending;
        pending = next;
      }

      for (var node = inOrder; node != null; node = node.next) {
        node.callback.accept(value, failureCause);
      }
    }
  }

  /** future is reusable after close */
  public boolean close() {
    final int prevState = UnsafeApi.getAndSetInt(this, STATE_OFFSET, CLOSED);

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
      completeExceptionally(requireNonNull(otherFuture.failureCause));
    } else {
      // value is set by complete() before this state is reachable
      complete(uncheckedCastToNonNull(otherFuture.value));
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

  private static final class CallbackNode<V extends @Nullable Object> {
    private final BiConsumer<V, @Nullable Throwable> callback;
    private @Nullable CallbackNode<V> next;

    private CallbackNode(final BiConsumer<V, @Nullable Throwable> callback) {
      this.callback = callback;
    }
  }
}
