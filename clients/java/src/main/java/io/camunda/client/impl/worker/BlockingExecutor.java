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
 *
 * <p>An executor built with reserved poll capacity can keep part of its slots for {@link
 * #executeWithoutWaiting(Runnable)}, the path polled jobs take, while {@link
 * #reservePollLane(boolean)} says the poll is being starved. A pushed job, which takes {@link
 * #execute(Runnable)}, can then hold only the remaining slots. When the lane is not reserved,
 * either path can take every slot.
 */
final class BlockingExecutor implements JobExecutor {
  private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

  private final Executor wrappedExecutor;
  private final Semaphore semaphore;

  // Sub-limit on the push path only. It equals the full capacity while the poll lane is not
  // reserved, so the push path can use every slot, and shrinks by `reservedPollCapacity` while the
  // lane is reserved, so those slots stay reachable only by the poll path. `semaphore` still bounds
  // total in-flight and remains the single capacity authority. Null when nothing can be reserved.
  private final ResizableSemaphore pushBudget;

  private final int reservedPollCapacity;
  private final AtomicBoolean pollLaneReserved = new AtomicBoolean(false);
  private final int maxCapacity;
  private final long timeoutMillis;
  private volatile Runnable capacityListener = () -> {};

  public BlockingExecutor(
      final Executor wrappedExecutor, final int maxActivate, final Duration jobActivationTimeout) {
    this(wrappedExecutor, maxActivate, jobActivationTimeout, 0);
  }

  /**
   * @param reservedPollCapacity how many of the {@code maxActivate} slots are kept reachable only
   *     by the poll path while the poll lane is reserved (see {@link #reservePollLane(boolean)}).
   *     The push path may then hold at most {@code maxActivate - reservedPollCapacity} at a time;
   *     while the lane is not reserved it may hold all of them. 0 disables the reservation and
   *     restores the single-pool behaviour.
   */
  public BlockingExecutor(
      final Executor wrappedExecutor,
      final int maxActivate,
      final Duration jobActivationTimeout,
      final int reservedPollCapacity) {
    this.wrappedExecutor = wrappedExecutor;
    semaphore = new Semaphore(maxActivate);
    final boolean canReserve = reservedPollCapacity > 0 && reservedPollCapacity < maxActivate;
    pushBudget = canReserve ? new ResizableSemaphore(maxActivate) : null;
    this.reservedPollCapacity = canReserve ? reservedPollCapacity : 0;
    maxCapacity = maxActivate;
    timeoutMillis = jobActivationTimeout.toMillis();
  }

  @Override
  public void execute(final Runnable command) throws RejectedExecutionException {
    // The push path. When a lane can be reserved it must first hold a budget permit, so while the
    // lane is reserved it can never take the slots kept for the poll. The budget is taken before
    // the capacity so a push that cannot fit within its budget is refused without ever holding a
    // capacity permit. Both acquisitions share one deadline so the whole dispatch is bounded by the
    // configured timeout, not by it twice.
    final long deadlineNanos = System.nanoTime() + TIMEOUT_UNIT.toNanos(timeoutMillis);
    acquirePushBudget(deadlineNanos);
    try {
      acquireCapacity(deadlineNanos);
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

  /**
   * Shrinks the push budget by the reserved slots, or gives them back. Shrinking never interrupts a
   * running job: if the push path already holds more than the smaller budget, the budget goes
   * negative and new pushes wait until enough pushed jobs finish. Every push takes a budget permit
   * whether or not the lane is reserved, so the count of pushed jobs in flight stays correct across
   * a change. Repeating the current state does nothing.
   */
  @Override
  public void reservePollLane(final boolean reserved) {
    if (pushBudget == null || !pollLaneReserved.compareAndSet(!reserved, reserved)) {
      return;
    }
    if (reserved) {
      pushBudget.reducePermits(reservedPollCapacity);
    } else {
      pushBudget.release(reservedPollCapacity);
    }
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

  private void acquirePushBudget(final long deadlineNanos) {
    if (pushBudget == null) {
      return;
    }
    try {
      if (!pushBudget.tryAcquire(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
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

  private void acquireCapacity(final long deadlineNanos) {
    try {
      if (!semaphore.tryAcquire(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS)) {
        throw new RejectedExecutionException(
            String.format("Not able to acquire lease in %d%s", timeoutMillis, TIMEOUT_UNIT));
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RejectedExecutionException(
          "Interrupted while waiting to acquire a lease to run the command", e);
    }
  }

  // Nanos left until the shared deadline, never negative so a passed deadline turns the acquire
  // into an immediate poll rather than an unbounded wait.
  private static long remainingNanos(final long deadlineNanos) {
    return Math.max(0L, deadlineNanos - System.nanoTime());
  }

  /**
   * Exposes {@link Semaphore#reducePermits(int)}, which is protected, to resize the push budget.
   */
  private static final class ResizableSemaphore extends Semaphore {
    private ResizableSemaphore(final int permits) {
      super(permits);
    }

    @Override
    protected void reducePermits(final int reduction) {
      super.reducePermits(reduction);
    }
  }
}
