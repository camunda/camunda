/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched.future;

import static org.agrona.UnsafeAccess.UNSAFE;

import io.zeebe.util.sched.ActorTask;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.FutureUtil;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Completable future implementation that is garbage free and reusable */
@SuppressWarnings("restriction")
public class CompletableActorFuture<V> implements ActorFuture<V> {
  private static final long STATE_OFFSET;

  private static final int AWAITING_RESULT = 1;
  private static final int COMPLETING = 2;
  private static final int COMPLETED = 3;
  private static final int COMPLETED_EXCEPTIONALLY = 4;
  private static final int CLOSED = 5;

  private final ManyToOneConcurrentLinkedQueue<ActorTask> blockedTasks =
      new ManyToOneConcurrentLinkedQueue<>();

  private volatile int state = CLOSED;

  private final ReentrantLock completionLock = new ReentrantLock();
  private Condition isDoneCondition;

  protected V value;
  protected String failure;
  protected Throwable failureCause;

  public CompletableActorFuture() {
    setAwaitingResult();
  }

  private CompletableActorFuture(V value) {
    this.value = value;
    this.state = COMPLETED;
  }

  private CompletableActorFuture(Throwable throwable) {
    ensureValidThrowable(throwable);
    this.failure = throwable.getMessage();
    this.failureCause = throwable;
    this.state = COMPLETED_EXCEPTIONALLY;
  }

  private void ensureValidThrowable(Throwable throwable) {
    if (throwable == null) {
      throw new NullPointerException("Throwable must not be null.");
    }
  }

  public void setAwaitingResult() {
    state = AWAITING_RESULT;
    isDoneCondition = completionLock.newCondition();
  }

  public static <V> CompletableActorFuture<V> completed(V result) {
    return new CompletableActorFuture<>(result); // cast for null result
  }

  public static <V> CompletableActorFuture<V> completedExceptionally(Throwable throwable) {
    return new CompletableActorFuture<>(throwable);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
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
  public boolean isCompletedExceptionally() {
    return state == COMPLETED_EXCEPTIONALLY;
  }

  public boolean isAwaitingResult() {
    return state == AWAITING_RESULT;
  }

  @Override
  public void block(ActorTask onCompletion) {
    blockedTasks.add(onCompletion);
  }

  @Override
  public V get() throws ExecutionException, InterruptedException {
    try {
      return get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public V get(long timeout, TimeUnit unit)
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

  @Override
  public void complete(V value) {
    if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
      this.value = value;
      this.state = COMPLETED;
      notifyBlockedTasks();
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
  public void completeExceptionally(String failure, Throwable throwable) {
    // important for other actors that consume this by #runOnCompletion
    ensureValidThrowable(throwable);

    if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING)) {
      this.failure = failure;
      this.failureCause = throwable;
      this.state = COMPLETED_EXCEPTIONALLY;
      notifyBlockedTasks();
    } else {
      final String err =
          "Cannot complete future, the future is already completed "
              + (state == COMPLETED_EXCEPTIONALLY
                  ? ("exceptionally with '" + failure + "' ")
                  : " with value " + value);
      throw new IllegalStateException(err);
    }
  }

  @Override
  public void completeExceptionally(Throwable throwable) {
    ensureValidThrowable(throwable);
    completeExceptionally(throwable.getMessage(), throwable);
  }

  private void notifyBlockedTasks() {
    notifyAllInQueue(blockedTasks);

    try {
      completionLock.lock();
      isDoneCondition.signalAll();
    } finally {
      completionLock.unlock();
    }
  }

  private void notifyAllInQueue(Queue<ActorTask> tasks) {
    while (!tasks.isEmpty()) {
      final ActorTask task = tasks.poll();

      if (task != null) {
        task.tryWakeup();
      }
    }
  }

  @Override
  public V join() {
    return FutureUtil.join(this);
  }

  /** future is reusable after close */
  public boolean close() {
    final int prevState = UNSAFE.getAndSetInt(this, STATE_OFFSET, CLOSED);

    if (prevState != CLOSED) {
      value = null;
      failure = null;
      failureCause = null;
      notifyBlockedTasks();
    }

    return prevState != CLOSED;
  }

  public boolean isClosed() {
    return state == CLOSED;
  }

  @Override
  public Throwable getException() {
    if (!isCompletedExceptionally()) {
      throw new IllegalStateException(
          "Cannot call getException(); future is not completed exceptionally.");
    }

    return failureCause;
  }

  static {
    try {
      STATE_OFFSET =
          UNSAFE.objectFieldOffset(CompletableActorFuture.class.getDeclaredField("state"));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void completeWith(CompletableActorFuture<V> otherFuture) {
    if (!otherFuture.isDone()) {
      throw new IllegalArgumentException(
          "Future is not completed, can't complete this future with uncompleted future.");
    }

    if (otherFuture.isCompletedExceptionally()) {
      this.completeExceptionally(otherFuture.failureCause);
    } else {
      this.complete(otherFuture.value);
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
}
