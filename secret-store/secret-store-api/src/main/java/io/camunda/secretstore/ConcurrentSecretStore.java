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
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link SecretStore} that pays one backend call per name ({@link
 * SecretStore#resolvesOneByOne}) by splitting the requested names into up to {@code maxConcurrency}
 * chunks and resolving them concurrently on a shared thread pool, instead of paying every round
 * trip back to back on the calling thread.
 *
 * <p>The pool is shared across every store this wraps rather than owned per instance: {@link
 * #close} therefore closes only the wrapped store, never the pool.
 *
 * <p>A store that already covers several names per call ({@code resolvesOneByOne() == false}), a
 * {@code maxConcurrency} of 1, or a request naming at most one secret all take the same single,
 * un-hopped call the wrapped store would have taken anyway; the fan-out below never runs for them.
 *
 * <p>If any chunk throws {@link SecretStoreUnavailableException}, the whole call fails with it once
 * every chunk has finished; the names of chunks that did succeed are not returned. They stay
 * pending and are retried on the next resolution cycle, exactly as they would if the whole
 * unwrapped store call had failed.
 */
@NullMarked
public final class ConcurrentSecretStore implements SecretStore {

  private final SecretStore delegate;
  private final ExecutorService pool;
  private final int maxConcurrency;

  public ConcurrentSecretStore(
      final SecretStore delegate, final ExecutorService pool, final int maxConcurrency) {
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException(
          "maxConcurrency must be at least 1, but was " + maxConcurrency);
    }
    this.delegate = delegate;
    this.pool = pool;
    this.maxConcurrency = maxConcurrency;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (!delegate.resolvesOneByOne() || maxConcurrency == 1 || names.size() <= 1) {
      return delegate.resolve(names);
    }

    final List<Callable<Map<String, SecretResolutionResult>>> tasks =
        chunk(names, maxConcurrency).stream()
            .<Callable<Map<String, SecretResolutionResult>>>map(
                chunkNames -> () -> delegate.resolve(chunkNames))
            .toList();
    final List<Future<Map<String, SecretResolutionResult>>> futures;
    try {
      futures = pool.invokeAll(tasks);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SecretStoreUnavailableException(
          "Interrupted while resolving secrets concurrently", e);
    }

    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    // every future is already done by the time invokeAll returns, so draining them all before
    // deciding whether to fail loses nothing and keeps the failure the same shape a single
    // unwrapped call would have thrown: one SecretStoreUnavailableException for the whole request.
    RuntimeException firstFailure = null;
    for (final var future : futures) {
      try {
        results.putAll(future.get());
      } catch (final ExecutionException e) {
        firstFailure = firstFailure == null ? unwrap(e) : firstFailure;
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        firstFailure =
            firstFailure == null
                ? new SecretStoreUnavailableException(
                    "Interrupted while resolving secrets concurrently", e)
                : firstFailure;
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
    return results;
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

  private static RuntimeException unwrap(final ExecutionException e) {
    final var cause = e.getCause();
    if (cause instanceof final RuntimeException runtimeException) {
      return runtimeException;
    }
    return new SecretStoreUnavailableException("Failed to resolve secrets concurrently", cause);
  }

  private static List<Set<String>> chunk(final Set<String> names, final int maxConcurrency) {
    final var all = new ArrayList<>(names);
    final var chunkCount = Math.min(maxConcurrency, all.size());
    final var chunkSize = (all.size() + chunkCount - 1) / chunkCount;
    final List<Set<String>> chunks = new ArrayList<>(chunkCount);
    for (int i = 0; i < all.size(); i += chunkSize) {
      chunks.add(new LinkedHashSet<>(all.subList(i, Math.min(i + chunkSize, all.size()))));
    }
    return chunks;
  }
}
