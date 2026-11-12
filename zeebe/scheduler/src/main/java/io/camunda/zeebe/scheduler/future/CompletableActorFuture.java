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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.ActorThread;
import io.camunda.zeebe.scheduler.FutureUtil;
import io.camunda.zeebe.util.Loggers;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Completable future implementation that is reusable after {@link #close()}. */
public final class CompletableActorFuture<V extends @Nullable Object> implements ActorFuture<V> {
  private static final VarHandle STATE;
  private static final VarHandle CALLBACKS;

  private static final int AWAITING_RESULT = 1;
  private static final int COMPLETING = 2;
  private static final int COMPLETED = 3;
  private static final int COMPLETED_EXCEPTIONALLY = 4;
  private static final int CLOSED = 5;

  static {
    try {
      final var lookup = MethodHandles.lookup();
      STATE = lookup.findVarHandle(CompletableActorFuture.class, "state", int.class);
      CALLBACKS = lookup.findVarHandle(CompletableActorFuture.class, "callbacks", Callback.class);
    } catch (final ReflectiveOperationException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  private long completedAt;
  private @Nullable V value;
  private @Nullable String failure;
  private @Nullable Throwable failureCause;

  // stack of callbacks to notify on completion, newest first; see pushCallback
  @SuppressFBWarnings(
      value = "UWF_UNWRITTEN_FIELD",
      justification = "Written using a VarHandle (see CALLBACKS usage)")
  private volatile @Nullable Callback<V> callbacks;

  private volatile int state = CLOSED;

  public CompletableActorFuture() {
    setAwaitingResult();
  }

  private CompletableActorFuture(final V value) {
    this.value = value;
    completedAt = System.nanoTime();
    state = COMPLETED;
  }

  private CompletableActorFuture(final Throwable throwable) {
    ensureValidThrowable(throwable);
    failure = throwable.getMessage();
    failureCause = throwable;
    completedAt = System.nanoTime();
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
    } else if (!isDone()) {
      awaitCompletion(timeout, unit);
    }

    if (isCompletedExceptionally()) {
      throw new ExecutionException(failure, failureCause);
    } else {
      // value is set by complete() before this state is reachable
      return uncheckedCastToNonNull(value);
    }
  }

  /** Blocks a non-actor thread until this future is done, or the timeout elapsed. */
  private void awaitCompletion(final long timeout, final TimeUnit unit)
      throws InterruptedException, TimeoutException {
    final long deadline = System.nanoTime() + unit.toNanos(timeout);
    Waiter<V> waiter = null;
    try {
      while (!isDone()) {
        if (waiter == null || waiter.isSignalled()) {
          // a signal without completion comes from close(), which consumes the waiter; register a
          // new one so that a later completion of the reused future still wakes this thread
          waiter = new Waiter<>(Thread.currentThread());
          pushCallback(waiter);
          continue;
        }

        if (Thread.interrupted()) {
          throw new InterruptedException();
        }

        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new TimeoutException("Timeout after: " + timeout + " " + unit);
        }
        LockSupport.parkNanos(this, remaining);
      }
    } finally {
      if (waiter != null) {
        waiter.cancel();
      }
    }
  }

  public boolean isAwaitingResult() {
    return state == AWAITING_RESULT;
  }

  @Override
  public void complete(final V value) {
    if (STATE.compareAndSet(this, AWAITING_RESULT, COMPLETING)) {
      this.value = value;
      state = COMPLETED;
      completedAt = System.nanoTime();
      notifyCallbacks();
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

    if (STATE.compareAndSet(this, AWAITING_RESULT, COMPLETING)) {
      this.failure = failure;
      failureCause = throwable;
      completedAt = System.nanoTime();
      state = COMPLETED_EXCEPTIONALLY;
      notifyCallbacks();
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
    pushCallback((resIgnore, errorIgnore) -> onCompletion.tryWakeup());
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
    // the callbacks. Then the consumer will never get executed. To ensure that the
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
      // If future is already completed, callbacks are not notified again. So there is no
      // need to add the consumer.
      pushCallback(checkedConsumer);
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

  /**
   * Pushes a callback onto a lock-free (Treiber) stack. A callback pushed after {@link
   * #notifyCallbacks()} took the stack is not notified, so a caller that must not miss the
   * completion checks {@link #isDone()} after pushing; the state is written before the stack is
   * taken, so that check sees it.
   */
  private void pushCallback(final BiConsumer<V, @Nullable Throwable> consumer) {
    final Callback<V> node = new Callback<>(consumer);
    Callback<V> head;
    do {
      head = callbacks;
      node.next = head;
    } while (!CALLBACKS.compareAndSet(this, head, node));
  }

  @SuppressWarnings("unchecked")
  private void notifyCallbacks() {
    Callback<V> node = (Callback<V>) CALLBACKS.getAndSet(this, null);

    // the stack is newest first; reverse it to notify in registration order
    Callback<V> oldestFirst = null;
    while (node != null) {
      final Callback<V> next = node.next;
      node.next = oldestFirst;
      oldestFirst = node;
      node = next;
    }

    for (; oldestFirst != null; oldestFirst = oldestFirst.next) {
      oldestFirst.consumer.accept(value, failureCause);
    }
  }

  /** future is reusable after close */
  public boolean close() {
    final int prevState = (int) STATE.getAndSet(this, CLOSED);

    if (prevState != CLOSED) {
      value = null;
      failure = null;
      failureCause = null;
      notifyCallbacks();
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

  private static final class Callback<V extends @Nullable Object> {
    private final BiConsumer<V, @Nullable Throwable> consumer;
    private @Nullable Callback<V> next;

    private Callback(final BiConsumer<V, @Nullable Throwable> consumer) {
      this.consumer = consumer;
    }
  }

  /** Wakes up a thread blocked in {@link #get(long, TimeUnit)}. */
  private static final class Waiter<V extends @Nullable Object>
      implements BiConsumer<V, @Nullable Throwable> {
    private volatile @Nullable Thread thread;
    private volatile boolean signalled;

    private Waiter(final Thread thread) {
      this.thread = thread;
    }

    @Override
    public void accept(final V ignoredValue, final @Nullable Throwable ignoredError) {
      signalled = true;
      final Thread waiting = thread;
      if (waiting != null) {
        LockSupport.unpark(waiting);
      }
    }

    private boolean isSignalled() {
      return signalled;
    }

    /** Once the thread stopped waiting, a late signal must not unpark it elsewhere. */
    private void cancel() {
      thread = null;
    }
  }
}
