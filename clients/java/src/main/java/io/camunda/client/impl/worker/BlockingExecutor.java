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
package io.camunda.client.impl.worker;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An executor that only takes a command when it has capacity to run it, waiting up to a fixed time
 * for capacity to become available.
 *
 * <p>Waiting blocks the calling thread, so it is only for a caller that has nothing better to do
 * with that thread. A caller that carries a response of the client on it uses {@link
 * #executeWithoutWaiting(Runnable)} instead.
 *
 * <p>A command is either handed to the wrapped executor or refused with a {@link
 * RejectedExecutionException}; it is never dropped without notice.
 *
 * <p>A refusal does not always mean the command did not run. The wrapped executor may run the
 * command on the calling thread and let it fail there, which reaches the caller as a refusal too.
 * What callers can rely on is the capacity: every command takes one slot and gives it back exactly
 * once, whether it ran or was refused.
 */
final class BlockingExecutor implements JobExecutor {
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

  private final Executor wrappedExecutor;
  private final Semaphore semaphore;

  // Prototype for #59734. When streaming is on, the push path (execute) and the poll path
  // (executeWithoutWaiting) both draw from `semaphore`, but they do not reach a freed permit at the
  // same speed: a pushed job is a thread already parked in acquireCapacity, unparked in
  // microseconds
  // on release, while a poll only issues its request a full network round-trip after a slot frees.
  // Under sustained push the poll loses nearly every freed slot, and since polling is the only path
  // that drains the ACTIVATABLE backlog and recovers timed-out jobs, that starves backlog drain.
  // The push path must therefore also hold a permit from this smaller budget, which leaves
  // `maxCapacity - pushBudget` slots that only the poll can ever take. `semaphore` still bounds
  // total
  // in-flight and remains the single capacity authority; `pushBudget` is a sub-limit on the push
  // path, not a second count of the same thing. It is null when there is nothing to reserve (no
  // streaming, so no push path to bound).
  private final Semaphore pushBudget;

  private final int maxCapacity;
  private final long timeoutMillis;
  private volatile Runnable capacityListener = () -> {};

  public BlockingExecutor(
      final Executor wrappedExecutor, final int maxActivate, final Duration jobActivationTimeout) {
    this(wrappedExecutor, maxActivate, jobActivationTimeout, 0);
  }

  /**
   * @param reservedPollCapacity how many of the {@code maxActivate} slots are kept reachable only
   *     by the poll path (the push path may hold at most {@code maxActivate - reservedPollCapacity}
   *     at a time). 0 disables the reservation and restores the single-pool behaviour.
   */
  public BlockingExecutor(
      final Executor wrappedExecutor,
      final int maxActivate,
      final Duration jobActivationTimeout,
      final int reservedPollCapacity) {
    this.wrappedExecutor = wrappedExecutor;
    semaphore = new Semaphore(maxActivate);
    pushBudget =
        reservedPollCapacity > 0 && reservedPollCapacity < maxActivate
            ? new Semaphore(maxActivate - reservedPollCapacity)
            : null;
    maxCapacity = maxActivate;
    timeoutMillis = jobActivationTimeout.toMillis();
  }

  @Override
  public void execute(final Runnable command) throws RejectedExecutionException {
    // The push path. When a lane is reserved it must first hold a budget permit, so it can never
    // take the slots kept for the poll. The budget is taken before the capacity so a push that
    // cannot fit within its budget is refused without ever holding a capacity permit.
    acquirePushBudget();
    try {
      acquireCapacity();
    } catch (final RuntimeException e) {
      releasePushBudget();
      throw e;
    }
    dispatch(command, pushBudget != null);
  }

  @Override
  public void executeWithoutWaiting(final Runnable command) throws RejectedExecutionException {
    // The poll path. It only takes a capacity permit, never a budget permit, so it can reach every
    // slot including the reserved ones.
    if (!semaphore.tryAcquire()) {
      throw new RejectedExecutionException("Not able to acquire a lease without waiting for one");
    }
    dispatch(command, false);
  }

  @Override
  public int freeCapacity() {
    return semaphore.availablePermits();
  }

  @Override
  public boolean hasNoJobsInFlight() {
    return semaphore.availablePermits() >= maxCapacity;
  }

  @Override
  public void onCapacityAvailable(final Runnable listener) {
    capacityListener = listener;
  }

  private void dispatch(final Runnable command, final boolean holdsPushBudget) {
    // The wrapped executor may run the command on the calling thread. A command that fails then
    // looks exactly like a command the executor refused, so both paths below can be taken for the
    // same command. The flag makes sure its capacity is given back only once, as giving it back
    // twice would let the executor run more commands at a time than it is allowed to.
    final AtomicBoolean capacityHeld = new AtomicBoolean(true);
    try {
      wrappedExecutor.execute(
          () -> {
            try {
              command.run();
            } finally {
              releaseCapacity(capacityHeld, holdsPushBudget);
            }
          });
    } catch (final RuntimeException | Error e) {
      // nothing else will give the capacity back, unless the command ran on the calling thread and
      // its finalizer already did, which is what the flag above is there to catch
      releaseCapacity(capacityHeld, holdsPushBudget);
      throw e;
    }
  }

  private void releaseCapacity(final AtomicBoolean capacityHeld, final boolean holdsPushBudget) {
    if (capacityHeld.compareAndSet(true, false)) {
      semaphore.release();
      if (holdsPushBudget) {
        releasePushBudget();
      }
      // Runs only after the permit is back, so a worker polling from here sees the freed slot.
      capacityListener.run();
    }
  }

  private void acquirePushBudget() {
    if (pushBudget == null) {
      return;
    }
    try {
      if (!pushBudget.tryAcquire(timeoutMillis, TIMEOUT_UNIT)) {
        throw new RejectedExecutionException(
            String.format("Not able to acquire a push lease in %d%s", timeoutMillis, TIMEOUT_UNIT));
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RejectedExecutionException(
          "Interrupted while waiting to acquire a push lease to run the command", e);
    }
  }

  private void releasePushBudget() {
    if (pushBudget != null) {
      pushBudget.release();
    }
  }

  private void acquireCapacity() {
    try {
      if (!semaphore.tryAcquire(timeoutMillis, TIMEOUT_UNIT)) {
        throw new RejectedExecutionException(
            String.format("Not able to acquire lease in %d%s", timeoutMillis, TIMEOUT_UNIT));
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RejectedExecutionException(
          "Interrupted while waiting to acquire a lease to run the command", e);
    }
  }
}
