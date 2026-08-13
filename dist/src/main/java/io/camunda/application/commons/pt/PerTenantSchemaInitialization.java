/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.zeebe.util.retry.RetryConfiguration;
import io.github.resilience4j.core.IntervalFunction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes every physical tenant's secondary-storage schema independently: one task per tenant,
 * each retrying in the background until it succeeds, with no failure ever aborting startup.
 * Storage-agnostic — the attempt, the terminal classification and the retry configuration are
 * supplied by the caller, so that Elasticsearch/OpenSearch and RDBMS can share this component.
 *
 * <p>{@link #startAndAwaitFirstOutcome()} blocks until every tenant has <em>settled</em>: it has
 * either initialized or failed at least once. Waiting for a first outcome rather than for the first
 * <em>ready</em> tenant is what keeps a rolling-upgrade node out of the load balancer until all its
 * tenants have migrated, while still letting a node whose storage is unreachable come up promptly —
 * a failing tenant settles within its storage client's connect or socket timeout. See ADR 004 D1/D2
 * ({@code docs/adr/management/004-per-physical-tenant-schema-initialization.md}).
 *
 * <p>{@link #isInitialized(String)} is a one-way latch: it asserts that the schema described in the
 * source code was applied, never that the tenant's storage is currently reachable (ADR 004 D3).
 */
@NullMarked
public final class PerTenantSchemaInitialization implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PerTenantSchemaInitialization.class);

  /**
   * Bounds the cause-chain walk of the terminal classification, so that a cyclic chain cannot spin
   * it forever.
   */
  private static final int MAX_CAUSE_DEPTH = 32;

  private final Consumer<String> attempt;
  private final Predicate<Throwable> terminal;
  private final Function<String, RetryConfiguration> retryConfig;

  private final Map<String, AtomicBoolean> initialized;
  private final CountDownLatch firstOutcome;
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  // written by the thread that starts the tasks, read by whichever thread closes this
  private final List<Thread> workers = new CopyOnWriteArrayList<>();

  /**
   * @param tenantIds the physical tenants to initialize, one background task each
   * @param attempt applies one tenant's schema in a single attempt; throws to report failure
   * @param terminal decides whether one failure will not be repaired by retrying — the cause chain
   *     is walked for the caller, so this only classifies a single throwable. Classification is
   *     observational: it selects the log level and stops that tenant's retry loop, but never
   *     changes node-level behavior (ADR 004 D5), so it does not need to be exhaustive.
   * @param retryConfig the backoff applied between a tenant's attempts
   */
  public PerTenantSchemaInitialization(
      final Set<String> tenantIds,
      final Consumer<String> attempt,
      final Predicate<Throwable> terminal,
      final Function<String, RetryConfiguration> retryConfig) {
    this.attempt = attempt;
    this.terminal = terminal;
    this.retryConfig = retryConfig;

    // insertion-ordered so that tasks are started, and logged, in the order the tenants are
    // configured in
    final var latches = new LinkedHashMap<String, AtomicBoolean>();
    tenantIds.forEach(tenantId -> latches.put(tenantId, new AtomicBoolean(false)));
    initialized = Collections.unmodifiableMap(latches);
    firstOutcome = new CountDownLatch(initialized.size());
  }

  /**
   * Starts one background task per physical tenant and blocks until every one of them has settled.
   * Returns normally however many tenants failed — a storage failure degrades its own tenant and
   * nothing else (ADR 004 D1).
   *
   * <p>The wait is interruptible so that a shutdown signal arriving mid-initialization releases it.
   * A shutdown-triggered interrupt is deliberately not re-raised: the Spring context refresh has to
   * be allowed to finish for the broker to shut down gracefully.
   */
  public void startAndAwaitFirstOutcome() {
    initialized.forEach(
        (tenantId, latch) -> {
          // virtual: a tenant's task is a storage call and a sleep, and a tenant that stays
          // degraded holds its thread for as long as it keeps retrying
          final var worker =
              Thread.ofVirtual()
                  .name("schema-init-" + tenantId)
                  .unstarted(() -> initializeTenant(tenantId, latch));
          // registered before it runs, so that a close() racing this loop still interrupts it
          workers.add(worker);
          worker.start();
        });

    try {
      firstOutcome.await();
    } catch (final InterruptedException e) {
      LOG.debug(
          "Interrupted while waiting for the first schema-initialization outcome of every physical"
              + " tenant. Shutdown signal is caught={}",
          shutdown.get(),
          e);
      if (!shutdown.get()) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Whether the physical tenant's schema has been applied. An unknown tenant is never ready. */
  public boolean isInitialized(final String physicalTenantId) {
    final AtomicBoolean latch = initialized.get(physicalTenantId);
    return latch != null && latch.get();
  }

  /** Stops all retrying and releases the barrier; idempotent. */
  @Override
  public void close() {
    if (!shutdown.compareAndSet(false, true)) {
      return;
    }
    workers.forEach(Thread::interrupt);
    // Release the barrier here rather than leaving it to the interrupted tasks: a task blocked in a
    // storage read may not observe the interrupt before its client's socket timeout, and shutdown
    // must not wait that long. Counting down a latch that already reached zero is a no-op.
    while (firstOutcome.getCount() > 0) {
      firstOutcome.countDown();
    }
  }

  private void initializeTenant(final String physicalTenantId, final AtomicBoolean latch) {
    boolean settled = false;

    try {
      // read inside the try: the barrier is only guaranteed to be released by the finally below,
      // so anything that can throw — an unusable retry configuration included — has to be in it,
      // or startup would block on a tenant that never produces an outcome
      final RetryConfiguration retry = retryConfig.apply(physicalTenantId);
      final int maxAttempts = retry.getMaxRetries();
      final var backoff =
          IntervalFunction.ofExponentialRandomBackoff(
              retry.getMinRetryDelay(), retry.getRetryDelayMultiplier(), retry.getMaxRetryDelay());

      for (int attemptNumber = 1; !shutdown.get(); attemptNumber++) {
        final long retryDelayMillis;
        try {
          attempt.accept(physicalTenantId);
          latch.set(true);
          logInitialized(physicalTenantId, attemptNumber);
          return;
        } catch (final Exception failure) {
          if (shutdown.get()) {
            LOG.debug(
                "Schema initialization for physical tenant '{}' failed during shutdown.",
                physicalTenantId,
                failure);
            return;
          }
          if (isTerminal(failure)) {
            LOG.error(
                "Schema initialization for physical tenant '{}' failed with a cause that retrying"
                    + " cannot repair, so it will not be retried. This tenant stays degraded, and"
                    + " its requests keep being rejected, until the cause is fixed and the node is"
                    + " restarted.",
                physicalTenantId,
                failure);
            return;
          }
          if (attemptNumber >= maxAttempts) {
            LOG.error(
                "Schema initialization for physical tenant '{}' failed on all {} configured"
                    + " attempts and will not be retried further. This tenant stays degraded, and"
                    + " its requests keep being rejected, until the node is restarted. Raise the"
                    + " retry max-retries to keep retrying.",
                physicalTenantId,
                maxAttempts,
                failure);
            return;
          }
          retryDelayMillis = backoff.apply(attemptNumber);
          LOG.warn(
              "Schema initialization for physical tenant '{}' failed on attempt {}, retrying in"
                  + " {}ms. This tenant stays degraded meanwhile.",
              physicalTenantId,
              attemptNumber,
              retryDelayMillis,
              failure);
        } finally {
          if (!settled) {
            settled = true;
            firstOutcome.countDown();
          }
        }

        if (!sleep(retryDelayMillis)) {
          return;
        }
      }
    } catch (final Exception unexpected) {
      LOG.error(
          "Schema initialization for physical tenant '{}' could not be run at all, so the tenant"
              + " stays degraded until the node is restarted. Other physical tenants are"
              + " unaffected.",
          physicalTenantId,
          unexpected);
    } finally {
      // Nothing above has released this tenant's share of the barrier if shutdown won the race
      // against the first attempt, or if the task could not be set up at all.
      if (!settled) {
        firstOutcome.countDown();
      }
    }
  }

  private void logInitialized(final String physicalTenantId, final int attemptNumber) {
    if (attemptNumber == 1) {
      LOG.info("Schema for physical tenant '{}' is initialized.", physicalTenantId);
    } else {
      LOG.info(
          "Schema for physical tenant '{}' is initialized after {} attempts, and the tenant is no"
              + " longer degraded.",
          physicalTenantId,
          attemptNumber);
    }
  }

  /** Returns false when the wait was cut short and this tenant's task should stop. */
  private boolean sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return !shutdown.get();
  }

  private boolean isTerminal(final Throwable failure) {
    Throwable cause = failure;
    for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (terminal.test(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }
}
