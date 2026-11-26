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
package io.camunda.client.impl.backpressure;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.impl.backpressure.BackpressureScheduler.Cancellable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An adaptive limit on the number of concurrently running requests of one client, which reacts to
 * the backpressure signals of the cluster. It is a port of the {@code BackpressureManager} of the
 * Camunda TypeScript SDK.
 *
 * <p>The limit is lifted as long as the cluster does not signal backpressure. On a signal it caps
 * the concurrency ({@link BackpressureSettings#initialMax()}) and scales the cap down on every
 * further signal: by the soft factor, and from {@link BackpressureSettings#severeThreshold()}
 * consecutive signals on, by the severe factor, never below the floor. Once the cap sits at the
 * floor in the severe state, every request that wants to start is additionally delayed, by an
 * amount that doubles with each signal and is cleared by the next success. While requests succeed,
 * the cap recovers: first by a fixed step up to the initial cap, then multiplicatively, and it is
 * lifted again after a sustained quiet period.
 *
 * <p>Waiting is asynchronous: {@link #acquire()} returns a future instead of blocking a thread. All
 * state is guarded by a single lock; futures are completed outside of it.
 *
 * <p>Differences to the TypeScript implementation:
 *
 * <ul>
 *   <li>The cap is applied only once the first signal arrived, as the TypeScript documentation
 *       describes. The TypeScript code applies the initial cap from the start. As the cap is not
 *       limiting before the first signal, the first signal scales it from the initial cap, so both
 *       end at the same value.
 *   <li>Permits are tracked per request instead of as a bare counter. A request that started while
 *       there was no limit does not count against the limit when it finishes, and the release of a
 *       request is ignored if the limit was lifted in the meantime.
 *   <li>When the cap is raised from unlimited again, it starts from the initial cap and not from a
 *       hardcoded 16.
 * </ul>
 */
public final class BackpressureManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(BackpressureManager.class);
  private static final long UNSET = Long.MIN_VALUE;

  private final BackpressureSettings cfg;
  private final boolean gating;
  private final LongSupplier clockMillis;
  private final BackpressureScheduler scheduler;

  private final Object lock = new Object();
  // everything below is guarded by lock
  private Severity severity = Severity.HEALTHY;
  private int consecutive;
  private long lastEventAt;
  private int permitsCurrent;
  private Integer permitsMax; // null means unlimited
  private int epoch;
  private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
  private long lastRecoverCheck = UNSET;
  private long healthySince = UNSET;
  private long backoffMs;
  private boolean closed;
  // true while there is neither a limit nor a delay; written under lock, read without, so that the
  // successes of a healthy client do not contend for the lock
  private volatile boolean idle = true;

  public BackpressureManager(
      final BackpressureSettings settings,
      final LongSupplier clockMillis,
      final BackpressureScheduler scheduler) {
    cfg = settings;
    gating = settings.isEnabled() && settings.isGating();
    this.clockMillis = clockMillis;
    this.scheduler = scheduler;
  }

  /**
   * Asks for permission to start a request. The future completes with a {@link Permit} once the
   * request may start, which is immediately unless the limit is reached. The permit must be
   * released when the request is done.
   *
   * <p>The future fails with a {@link BackpressureQueueFullException} when too many requests are
   * already waiting. Completing the future exceptionally, e.g. by cancelling it, withdraws the
   * request from the queue.
   */
  public CompletableFuture<Permit> acquire() {
    final CompletableFuture<Permit> future = new CompletableFuture<>();
    final long delay;
    synchronized (lock) {
      if (closed) {
        future.completeExceptionally(closedException());
        return future;
      }
      if (!gating || permitsMax == null) {
        future.complete(new Permit(this, false, epoch));
        return future;
      }
      delay = backoffMs;
    }

    if (delay > 0) {
      // at the floor of a severe state: rate limit by delaying every request before it competes
      final Cancellable timer = scheduler.schedule(() -> admit(future), delay);
      future.whenComplete(
          (permit, error) -> {
            if (error != null) {
              timer.cancel();
            }
          });
    } else {
      admit(future);
    }
    return future;
  }

  /** Records that the cluster rejected a request because of backpressure. */
  public void recordBackpressure() {
    synchronized (lock) {
      if (!cfg.isEnabled()) {
        return;
      }
      lastEventAt = clockMillis.getAsLong();
      consecutive++;
      healthySince = UNSET;
      if (gating && permitsMax == null) {
        permitsMax = cfg.initialMax();
        permitsCurrent = Math.min(permitsCurrent, permitsMax);
      }

      final Severity previous = severity;
      if (consecutive >= cfg.severeThreshold()) {
        severity = Severity.SEVERE;
        if (gating) {
          scalePermits(cfg.severeFactor());
        }
      } else if (severity == Severity.HEALTHY) {
        severity = Severity.SOFT;
        if (gating) {
          scalePermits(cfg.softFactor());
        }
      } else if (severity == Severity.SOFT && gating) {
        scalePermits(cfg.softFactor());
      }

      if (gating
          && permitsMax != null
          && permitsMax <= cfg.floor()
          && severity == Severity.SEVERE) {
        backoffMs =
            backoffMs == 0
                ? cfg.backoffInitialMs()
                : Math.min(cfg.backoffMaxMs(), (long) (backoffMs * cfg.backoffEscalate()));
        LOGGER.debug("Backpressure delay at the floor is now {} ms", backoffMs);
      }

      if (severity != previous) {
        logSeverityChange(previous, severity);
      }
      refreshIdle();
    }
  }

  /**
   * Records that a request succeeded, which clears the delay at the floor and lets the limit
   * recover.
   */
  public void recordSuccess() {
    if (idle) {
      // nothing to clear and nothing to recover
      return;
    }
    List<Grant> grants = null;
    synchronized (lock) {
      if (!cfg.isEnabled()) {
        return;
      }
      if (backoffMs > 0) {
        backoffMs = 0;
        LOGGER.debug("Backpressure delay cleared after a successful request");
      }
      grants = maybeRecover(clockMillis.getAsLong(), grants);
      refreshIdle();
    }
    complete(grants);
  }

  private void refreshIdle() {
    idle = permitsMax == null && backoffMs == 0;
  }

  public State state() {
    synchronized (lock) {
      return new State(
          severity, consecutive, permitsMax, permitsCurrent, waiters.size(), backoffMs);
    }
  }

  /** Fails everything that still waits for a permit. */
  public void close() {
    final List<Waiter> pending;
    synchronized (lock) {
      closed = true;
      pending = new ArrayList<>(waiters);
      waiters.clear();
    }
    for (final Waiter waiter : pending) {
      waiter.future.completeExceptionally(closedException());
    }
  }

  private void admit(final CompletableFuture<Permit> future) {
    Permit granted = null;
    Throwable rejected = null;
    Waiter queued = null;
    synchronized (lock) {
      if (future.isDone()) {
        return;
      }
      if (closed) {
        rejected = closedException();
      } else if (permitsMax == null) {
        granted = new Permit(this, false, epoch);
      } else if (permitsCurrent < permitsMax) {
        permitsCurrent++;
        granted = new Permit(this, true, epoch);
      } else if (waiters.size() >= cfg.maxWaiters()) {
        rejected = new BackpressureQueueFullException(cfg.maxWaiters());
      } else {
        queued = new Waiter(future);
        waiters.add(queued);
      }
    }

    if (granted != null) {
      grant(future, granted);
    } else if (rejected != null) {
      future.completeExceptionally(rejected);
    } else {
      final Waiter waiter = queued;
      future.whenComplete(
          (permit, error) -> {
            if (error != null) {
              synchronized (lock) {
                waiters.remove(waiter);
              }
            }
          });
    }
  }

  private void release(final Permit permit) {
    if (!permit.released.compareAndSet(false, true)) {
      return;
    }
    final List<Grant> grants;
    synchronized (lock) {
      if (permit.counted && permit.epoch == epoch && permitsCurrent > 0) {
        permitsCurrent--;
      }
      grants = drain(null);
    }
    complete(grants);
  }

  /** Hands out as many permits to waiting requests as the limit allows. Holds the lock. */
  private List<Grant> drain(final List<Grant> into) {
    List<Grant> grants = into;
    while (!waiters.isEmpty()) {
      if (permitsMax != null && permitsCurrent >= permitsMax) {
        break;
      }
      final Waiter waiter = waiters.poll();
      if (waiter.future.isDone()) {
        continue;
      }
      if (grants == null) {
        grants = new ArrayList<>();
      }
      if (permitsMax == null) {
        grants.add(new Grant(waiter.future, new Permit(this, false, epoch)));
      } else {
        permitsCurrent++;
        grants.add(new Grant(waiter.future, new Permit(this, true, epoch)));
      }
    }
    return grants;
  }

  private static void complete(final List<Grant> grants) {
    if (grants != null) {
      for (final Grant grant : grants) {
        grant(grant.future, grant.permit);
      }
    }
  }

  private static void grant(final CompletableFuture<Permit> future, final Permit permit) {
    // the request may have gone away while it waited, in which case the permit is not needed
    if (!future.complete(permit)) {
      permit.release();
    }
  }

  private void scalePermits(final double factor) {
    if (permitsMax == null) {
      return;
    }
    final int next = Math.max(cfg.floor(), (int) Math.ceil(permitsMax * factor));
    if (next < permitsMax) {
      permitsMax = next;
      LOGGER.debug("Backpressure concurrency limit scaled down to {}", permitsMax);
    }
  }

  /** Holds the lock. Returns the grants that became possible, possibly {@code grants} itself. */
  private List<Grant> maybeRecover(final long now, final List<Grant> grants) {
    if (permitsMax == null || !gating) {
      return grants;
    }
    if (lastRecoverCheck != UNSET && now - lastRecoverCheck < cfg.recoveryIntervalMs()) {
      return grants;
    }
    lastRecoverCheck = now;

    if (now - lastEventAt > cfg.decayQuietMs()) {
      final Severity previous = severity;
      if (severity == Severity.SEVERE) {
        severity = Severity.SOFT;
      } else if (severity == Severity.SOFT) {
        severity = Severity.HEALTHY;
        healthySince = now;
      }
      if (severity == Severity.HEALTHY) {
        consecutive = 0;
      }
      if (previous != severity) {
        backoffMs = 0;
        logSeverityChange(previous, severity);
      }
    }

    final int bootstrapCap = cfg.initialMax();
    if (severity != Severity.HEALTHY) {
      // additive recovery up to the initial cap
      if (permitsMax < bootstrapCap) {
        permitsMax = Math.min(bootstrapCap, permitsMax + cfg.recoveryStep());
        if (permitsMax > cfg.floor()) {
          backoffMs = 0;
        }
        LOGGER.debug("Backpressure concurrency limit recovered to {}", permitsMax);
        return drain(grants);
      }
      return grants;
    }

    if (healthySince != UNSET && now - healthySince >= cfg.unlimitedAfterHealthyMs()) {
      permitsMax = null;
      permitsCurrent = 0;
      backoffMs = 0;
      epoch++;
      LOGGER.debug("Backpressure concurrency limit lifted after a sustained healthy period");
      return drain(grants);
    }

    // multiplicative growth beyond the initial cap while healthy
    final long next = (long) Math.ceil(permitsMax * cfg.healthyRecoveryMultiplier());
    if (next > permitsMax) {
      permitsMax = (int) Math.min(Integer.MAX_VALUE, next);
      LOGGER.debug("Backpressure concurrency limit grew to {}", permitsMax);
      return drain(grants);
    }
    return grants;
  }

  private static void logSeverityChange(final Severity from, final Severity to) {
    // crossing the boundary between healthy and unhealthy matters to operators, the rest is detail
    final boolean boundary = from == Severity.HEALTHY || to == Severity.HEALTHY;
    if (boundary) {
      LOGGER.info("Backpressure state changed from {} to {}", from, to);
    } else {
      LOGGER.debug("Backpressure state changed from {} to {}", from, to);
    }
  }

  private static ClientException closedException() {
    return new ClientException("Expected to send the request, but the client is closed");
  }

  public enum Severity {
    HEALTHY,
    SOFT,
    SEVERE
  }

  /** A snapshot of the limiter. */
  public static final class State {
    private final Severity severity;
    private final int consecutive;
    private final Integer permitsMax;
    private final int permitsCurrent;
    private final int waiters;
    private final long backoffMs;

    State(
        final Severity severity,
        final int consecutive,
        final Integer permitsMax,
        final int permitsCurrent,
        final int waiters,
        final long backoffMs) {
      this.severity = severity;
      this.consecutive = consecutive;
      this.permitsMax = permitsMax;
      this.permitsCurrent = permitsCurrent;
      this.waiters = waiters;
      this.backoffMs = backoffMs;
    }

    public Severity severity() {
      return severity;
    }

    /** The number of consecutive signals since the last time the state was healthy. */
    public int consecutive() {
      return consecutive;
    }

    /** The current limit, {@code null} while there is none. */
    public Integer permitsMax() {
      return permitsMax;
    }

    /** The number of running requests that count against the limit. */
    public int permitsCurrent() {
      return permitsCurrent;
    }

    /** The number of requests waiting for a permit. */
    public int waiters() {
      return waiters;
    }

    /** The delay applied to requests at the floor of a severe state, 0 if there is none. */
    public long backoffMs() {
      return backoffMs;
    }
  }

  /** The permission for one request to run; releasing it more than once has no effect. */
  public static final class Permit {
    private final BackpressureManager owner;
    private final boolean counted;
    private final int epoch;
    private final AtomicBoolean released = new AtomicBoolean();

    private Permit(final BackpressureManager owner, final boolean counted, final int epoch) {
      this.owner = owner;
      this.counted = counted;
      this.epoch = epoch;
    }

    public void release() {
      owner.release(this);
    }
  }

  private static final class Waiter {
    private final CompletableFuture<Permit> future;

    private Waiter(final CompletableFuture<Permit> future) {
      this.future = future;
    }
  }

  private static final class Grant {
    private final CompletableFuture<Permit> future;
    private final Permit permit;

    private Grant(final CompletableFuture<Permit> future, final Permit permit) {
      this.future = future;
      this.permit = permit;
    }
  }
}
