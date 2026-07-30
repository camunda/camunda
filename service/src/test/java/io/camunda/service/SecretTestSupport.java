/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Shared doubles for the tests of {@link SecretServices}. */
final class SecretTestSupport {

  private static final ExecutorService SAME_THREAD_EXECUTOR = new SameThreadExecutorService();

  private SecretTestSupport() {}

  /**
   * An executor provider that runs the store lookup on the calling thread, so the futures the
   * service hands back are already complete when a test joins them.
   *
   * <p>Stubbed rather than real: a real provider wraps the executor in the physical-tenant
   * propagating decorator, which needs Spring's web request context that this module's tests do not
   * have on the classpath. Note {@link SecretServices} reads the executor while being constructed,
   * so the stub has to be in place before then, which it is.
   */
  static ApiServicesExecutorProvider sameThreadExecutorProvider() {
    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(SAME_THREAD_EXECUTOR);
    return executorProvider;
  }

  /**
   * A map-backed {@link SecretStore} that records what it was asked for, so a test can assert that
   * the cache spares the store and that only authorized, valid references ever reach it.
   */
  static final class TestSecretStore implements SecretStore {

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, SecretResolutionResult.Failed> failures = new HashMap<>();
    private final List<Set<String>> resolveCalls = new ArrayList<>();
    // names this store lists but holds no value for, so a test can list one the SPI's nullness
    // contract forbids
    private final List<String> additionalListedNames = new ArrayList<>();
    private int listCalls;
    private boolean unavailable;
    private boolean omitResults;

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      resolveCalls.add(Set.copyOf(names));
      failIfUnavailable();
      final Map<String, SecretResolutionResult> results = new LinkedHashMap<>();
      if (omitResults) {
        return results;
      }
      names.forEach(name -> results.put(name, resultFor(name)));
      return results;
    }

    @Override
    public List<String> list() {
      listCalls++;
      failIfUnavailable();
      final List<String> names = new ArrayList<>(values.keySet());
      names.addAll(additionalListedNames);
      return names;
    }

    TestSecretStore holds(final String name, final String value) {
      values.put(name, value);
      return this;
    }

    /**
     * Makes the store list the given name without holding a value for it, {@code null} included.
     */
    void alsoLists(final String name) {
      additionalListedNames.add(name);
    }

    void failsResolving(
        final String name,
        final io.camunda.secretstore.SecretErrorCode code,
        final String message) {
      failures.put(name, new SecretResolutionResult.Failed(code, message, null));
    }

    /** Makes the store answer without an entry for the names it was asked for. */
    void omitsResults() {
      omitResults = true;
    }

    void isUnavailable() {
      unavailable = true;
    }

    List<Set<String>> resolveCalls() {
      return resolveCalls;
    }

    int listCalls() {
      return listCalls;
    }

    private SecretResolutionResult resultFor(final String name) {
      final var failure = failures.get(name);
      if (failure != null) {
        return failure;
      }
      final var value = values.get(name);
      return value == null
          ? new SecretResolutionResult.Failed(
              io.camunda.secretstore.SecretErrorCode.NOT_FOUND, "No secret found: " + name, null)
          : new SecretResolutionResult.Resolved(value);
    }

    private void failIfUnavailable() {
      if (unavailable) {
        throw new SecretStoreUnavailableException("the test store is unavailable");
      }
    }
  }

  private static final class SameThreadExecutorService extends AbstractExecutorService {

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(final Runnable command) {
      command.run();
    }
  }
}
