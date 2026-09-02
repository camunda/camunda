/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@link SecretStore} whose cost scales with the number of sequential backend calls it
 * issues ({@link SecretStore#namesPerCall}) by splitting the requested names into chunks of that
 * size and resolving them concurrently, instead of paying every round trip back to back on the
 * calling thread.
 *
 * <p>Each chunk runs as its own task on {@code pool} (a virtual-thread-per-task executor in
 * production, so a chunk waiting on a permit costs no platform thread), gated by {@code semaphore}
 * so that no more than its permit count of backend calls are in flight at once. Both are shared
 * across every store this wraps and across every caller of this store (every partition's scheduler
 * and the REST resolution path alike): {@link #close} therefore closes only the wrapped store,
 * never the pool or the semaphore.
 *
 * <p>A store that already covers the whole request in one call ({@code namesPerCall() >=
 * names.size()}), or a semaphore of one permit, take the same single, un-hopped call the wrapped
 * store would have taken anyway; the fan-out below never runs for them. The one-permit case is
 * checked explicitly (rather than left to the semaphore to serialize the chunks one at a time) so
 * that configuring a concurrency of 1 keeps meaning exactly what it means today: one call at a
 * time, on the calling thread, with no thread hop at all.
 *
 * <p>The bound is the semaphore's own permit count, read once here rather than passed alongside it:
 * the semaphore is the only thing that actually limits how many backend calls run at once, so a
 * separately configured number could only ever disagree with it. Reading it at construction is
 * accurate because a store is built at startup, before any chunk can hold a permit.
 *
 * <p>If any chunk throws {@link SecretStoreUnavailableException}, every chunk that has not yet
 * issued its backend call is skipped rather than dispatched: once a store is known to be
 * unavailable, letting the remaining chunks call it anyway would only pile more requests onto a
 * backend that is already failing (e.g. throttling). The whole call then fails with the first such
 * failure once every chunk has finished; the names of chunks that did succeed are not returned.
 * They stay pending and are retried on the next resolution cycle, exactly as they would if the
 * whole unwrapped store call had failed.
 */
@NullMarked
public final class ConcurrentSecretStore implements SecretStore {

  private final SecretStore delegate;
  private final ExecutorService pool;
  private final Semaphore semaphore;
  private final int maxConcurrency;

  public ConcurrentSecretStore(
      final SecretStore delegate, final ExecutorService pool, final Semaphore semaphore) {
    maxConcurrency = semaphore.availablePermits();
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException(
          "The shared secret resolution semaphore must carry at least one permit, but carried "
              + maxConcurrency);
    }
    this.delegate = delegate;
    this.pool = pool;
    this.semaphore = semaphore;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    final int callSize = delegate.namesPerCall();
    if (maxConcurrency == 1 || callSize >= names.size()) {
      return delegate.resolve(names);
    }

    // flips once any chunk observes the store is unavailable, so a chunk not yet past the check
    // below skips its own call instead of piling onto an already-failing backend
    final var storeUnavailable = new AtomicBoolean();
    final List<Callable<Map<String, SecretResolutionResult>>> tasks =
        chunk(names, callSize).stream()
            .<Callable<Map<String, SecretResolutionResult>>>map(
                chunkNames -> () -> resolveChunk(chunkNames, storeUnavailable))
            .toList();
    final List<Future<Map<String, SecretResolutionResult>>> futures;
    try {
      futures = pool.invokeAll(tasks);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SecretStoreUnavailableException(
          "Interrupted while resolving secrets concurrently", e);
    } catch (final RejectedExecutionException e) {
      // the pool is shut down (startup rollback has closed it, or the application is stopping), so
      // the store was never read. That is what SecretStoreUnavailableException means, and it is the
      // only failure the scheduler retries and the REST path reports as UNAVAILABLE: left as a
      // RejectedExecutionException it would instead be logged as an unexpected engine error.
      throw new SecretStoreUnavailableException(
          "Secret resolution pool rejected the concurrent resolution of " + names.size() + " names",
          e);
    }

    final Map<String, SecretResolutionResult> results =
        new LinkedHashMap<>((int) (names.size() / 0.75f) + 1);
    // every future is already done by the time invokeAll returns, so draining them all before
    // deciding whether to fail loses nothing and keeps the failure the same shape a single
    // unwrapped call would have thrown: one SecretStoreUnavailableException for the whole request.
    // Later failures are attached as suppressed rather than dropped, so a caller inspecting the
    // thrown exception can still see every chunk that failed, not just the first.
    RuntimeException firstFailure = null;
    for (final var future : futures) {
      try {
        results.putAll(future.get());
      } catch (final ExecutionException e) {
        firstFailure = addFailure(firstFailure, unwrap(e));
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        // the caller is a long-lived shared actor thread, not a short-lived worker: restoring the
        // flag here is still correct (this thread did not choose to stop waiting, whatever
        // interrupted it did), it just means the next blocking call on this same thread also sees
        // the flag set. Nothing in this codebase interrupts actor threads today, so this path is
        // not expected to be reachable in production.
        firstFailure =
            addFailure(
                firstFailure,
                new SecretStoreUnavailableException(
                    "Interrupted while resolving secrets concurrently", e));
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
    return results;
  }

  /**
   * Resolves one chunk, skipping the backend call entirely if a sibling chunk has already reported
   * the store unavailable. The store is checked again after the permit is acquired, in case it
   * flipped while this chunk was waiting: a chunk queued behind a full semaphore should not issue a
   * call into a store just found unavailable by the chunk that freed the permit it is about to
   * take.
   */
  private Map<String, SecretResolutionResult> resolveChunk(
      final Set<String> chunkNames, final AtomicBoolean storeUnavailable)
      throws InterruptedException {
    if (storeUnavailable.get()) {
      throw skippedException();
    }
    semaphore.acquire();
    try {
      if (storeUnavailable.get()) {
        throw skippedException();
      }
      return delegate.resolve(chunkNames);
    } catch (final SecretStoreUnavailableException e) {
      storeUnavailable.set(true);
      throw e;
    } finally {
      semaphore.release();
    }
  }

  private static SecretStoreUnavailableException skippedException() {
    return new SecretStoreUnavailableException(
        "Skipped: a concurrently-dispatched chunk already reported this store unavailable");
  }

  private static RuntimeException addFailure(
      final @Nullable RuntimeException firstFailure, final RuntimeException newFailure) {
    if (firstFailure == null) {
      return newFailure;
    }
    firstFailure.addSuppressed(newFailure);
    return firstFailure;
  }

  @Override
  public List<String> list() {
    return delegate.list();
  }

  @Override
  public void close() {
    delegate.close();
  }

  /**
   * Answers for the store this wraps rather than for this decorator, so a caller asking which store
   * was configured gets the same answer whether or not concurrency wrapped it.
   */
  @Override
  public boolean is(final Class<? extends SecretStore> storeType) {
    return delegate.is(storeType);
  }

  /**
   * Turns a chunk's failure into the exception this call fails with. An {@link Error} is rethrown
   * as it is instead: it is not one of the outcomes a store models, and reporting it as {@link
   * SecretStoreUnavailableException} would put the scheduler into a retry ladder over a JVM failure
   * it can neither retry away nor see.
   */
  private static RuntimeException unwrap(final ExecutionException e) {
    final var cause = e.getCause();
    if (cause instanceof final Error error) {
      throw error;
    }
    if (cause instanceof final RuntimeException runtimeException) {
      return runtimeException;
    }
    return new SecretStoreUnavailableException("Failed to resolve secrets concurrently", cause);
  }

  private static List<Set<String>> chunk(final Set<String> names, final int callSize) {
    final var all = new ArrayList<>(names);
    final List<Set<String>> chunks = new ArrayList<>();
    for (int i = 0; i < all.size(); i += callSize) {
      chunks.add(new LinkedHashSet<>(all.subList(i, Math.min(i + callSize, all.size()))));
    }
    return chunks;
  }
}
