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
  private final long timeoutMillis;

  public BlockingExecutor(
      final Executor wrappedExecutor, final int maxActivate, final Duration jobActivationTimeout) {
    this.wrappedExecutor = wrappedExecutor;
    semaphore = new Semaphore(maxActivate);
    timeoutMillis = jobActivationTimeout.toMillis();
  }

  @Override
  public void execute(final Runnable command) throws RejectedExecutionException {
    acquireCapacity();
    dispatch(command);
  }

  @Override
  public void executeWithoutWaiting(final Runnable command) throws RejectedExecutionException {
    if (!semaphore.tryAcquire()) {
      throw new RejectedExecutionException("Not able to acquire a lease without waiting for one");
    }
    dispatch(command);
  }

  @Override
  public int freeCapacity() {
    return semaphore.availablePermits();
  }

  private void dispatch(final Runnable command) {
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
              releaseCapacity(capacityHeld);
            }
          });
    } catch (final RuntimeException | Error e) {
      // nothing else will give the capacity back, unless the command ran on the calling thread and
      // its finalizer already did, which is what the flag above is there to catch
      releaseCapacity(capacityHeld);
      throw e;
    }
  }

  private void releaseCapacity(final AtomicBoolean capacityHeld) {
    if (capacityHeld.compareAndSet(true, false)) {
      semaphore.release();
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
