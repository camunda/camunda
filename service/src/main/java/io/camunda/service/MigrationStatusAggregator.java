/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects every registered {@link MigrationStatusProvider} and combines their per-physical-tenant
 * statuses into one {@code Map<physicalTenantId, Map<conditionName, MigrationConditionStatus>>}.
 *
 * <p>Providers make blocking calls, so they run on a dedicated pool and not on the shared REST API
 * executor.
 */
public class MigrationStatusAggregator implements AutoCloseable {

  /** Above the 5s the broker-facing providers give themselves, so their partial answers survive. */
  static final Duration DEFAULT_PROVIDER_TIMEOUT = Duration.ofSeconds(7);

  private static final Logger LOG = LoggerFactory.getLogger(MigrationStatusAggregator.class);

  private final List<MigrationStatusProvider> providers;
  private final Duration providerTimeout;
  private final ExecutorService executor = newProviderExecutor();
  private final Set<String> knownPhysicalTenantIds = ConcurrentHashMap.newKeySet();

  private final Object pollLock = new Object();
  // A provider call that outlives its poll is waited on again, not started again.
  private final List<ProviderCall> runningCalls;
  private CompletableFuture<Map<String, Map<String, MigrationConditionStatus>>> inFlightPoll;

  public MigrationStatusAggregator(final List<MigrationStatusProvider> providers) {
    this(providers, DEFAULT_PROVIDER_TIMEOUT);
  }

  @VisibleForTesting
  MigrationStatusAggregator(
      final List<MigrationStatusProvider> providers, final Duration providerTimeout) {
    this.providers = List.copyOf(providers);
    this.providerTimeout = providerTimeout;
    runningCalls = new ArrayList<>();
    providers.forEach(provider -> runningCalls.add(null));
  }

  /** Blocks the calling thread, for at most about the provider timeout, until the poll is done. */
  public Map<String, Map<String, MigrationConditionStatus>> aggregate() {
    return aggregateAsync().join();
  }

  /**
   * Polls every provider and returns the combined statuses. Callers that arrive while a poll is in
   * flight share it. A provider that does not answer within the provider timeout is reported as
   * {@code UNKNOWN}.
   *
   * <p>A provider call that outlives its poll is waited on again by the next one, so an answer can
   * be as old as that call. Providers only report progress, so that errs towards "not migrated".
   *
   * <p>Once the aggregator is closed, a poll that has to start a provider call fails.
   */
  public CompletableFuture<Map<String, Map<String, MigrationConditionStatus>>> aggregateAsync() {
    synchronized (pollLock) {
      if (inFlightPoll == null || inFlightPoll.isDone()) {
        try {
          inFlightPoll = poll();
        } catch (final RejectedExecutionException e) {
          return CompletableFuture.failedFuture(e);
        }
      }
      // A copy, so one caller cannot complete or cancel the shared poll.
      return inFlightPoll.copy();
    }
  }

  @Override
  public void close() {
    executor.shutdown();
  }

  private CompletableFuture<Map<String, Map<String, MigrationConditionStatus>>> poll() {
    final var conditionNames =
        providers.stream().map(MigrationStatusProvider::conditionName).toList();

    // Poll every provider concurrently rather than one at a time, so a slow provider doesn't add
    // its own timeout on top of every other provider's.
    final var pendingStatusesByProvider =
        new ArrayList<CompletableFuture<Map<String, MigrationConditionStatus>>>();
    for (int i = 0; i < providers.size(); i++) {
      pendingStatusesByProvider.add(pollProvider(i));
    }

    // Not thenApply: the last provider may complete on the shared timer thread.
    return CompletableFuture.allOf(pendingStatusesByProvider.toArray(CompletableFuture<?>[]::new))
        .thenApplyAsync(ignored -> merge(pendingStatusesByProvider, conditionNames));
  }

  private CompletableFuture<Map<String, MigrationConditionStatus>> pollProvider(final int index) {
    final var provider = providers.get(index);
    var call = runningCalls.get(index);
    if (call == null || call.isDone()) {
      call =
          new ProviderCall(
              CompletableFuture.supplyAsync(() -> safeGetMigrationStatus(provider), executor));
      runningCalls.set(index, call);
    }
    return call.awaitFor(providerTimeout)
        .exceptionally(
            error -> {
              if (error instanceof TimeoutException) {
                LOG.warn(
                    "Upgrade-readiness provider '{}' gave no answer within {}; reporting UNKNOWN.",
                    provider.conditionName(),
                    providerTimeout);
              } else {
                LOG.warn(
                    "Upgrade-readiness provider '{}' failed; reporting UNKNOWN.",
                    provider.conditionName(),
                    error);
              }
              return Map.of();
            });
  }

  @VisibleForTesting
  ProviderCall runningCall(final int index) {
    synchronized (pollLock) {
      return runningCalls.get(index);
    }
  }

  private Map<String, Map<String, MigrationConditionStatus>> merge(
      final List<CompletableFuture<Map<String, MigrationConditionStatus>>> pendingStatuses,
      final List<String> conditionNames) {
    final var physicalTenants = new LinkedHashMap<String, Map<String, MigrationConditionStatus>>();
    for (int i = 0; i < providers.size(); i++) {
      final var conditionName = conditionNames.get(i);
      final var freshStatuses = pendingStatuses.get(i).join();
      knownPhysicalTenantIds.addAll(freshStatuses.keySet());
      freshStatuses.forEach(
          (physicalTenantId, status) ->
              physicalTenants
                  .computeIfAbsent(physicalTenantId, ignored -> new LinkedHashMap<>())
                  .put(conditionName, status));
    }
    backfillMissingPairs(physicalTenants, conditionNames);
    return physicalTenants;
  }

  private static ExecutorService newProviderExecutor() {
    return Executors.newCachedThreadPool(
        new ThreadFactory() {
          private final AtomicInteger counter = new AtomicInteger();

          @Override
          public Thread newThread(final Runnable runnable) {
            final var thread =
                new Thread(runnable, "upgrade-readiness-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
          }
        });
  }

  private Map<String, MigrationConditionStatus> safeGetMigrationStatus(
      final MigrationStatusProvider provider) {
    try {
      return provider.getMigrationStatus();
    } catch (final Exception e) {
      LOG.warn(
          "Upgrade-readiness provider '{}' failed; no fresh status for this poll.",
          provider.conditionName(),
          e);
      return Map.of();
    }
  }

  /**
   * Fills in every (known physical tenant, registered condition) pair this poll did not freshly
   * report — whether because a whole provider call failed, or because that provider simply did not
   * report that tenant this cycle — with {@code UNKNOWN}.
   */
  private void backfillMissingPairs(
      final Map<String, Map<String, MigrationConditionStatus>> physicalTenants,
      final List<String> conditionNames) {
    for (final var physicalTenantId : knownPhysicalTenantIds) {
      final var conditions =
          physicalTenants.computeIfAbsent(physicalTenantId, ignored -> new LinkedHashMap<>());
      for (final var conditionName : conditionNames) {
        conditions.computeIfAbsent(
            conditionName,
            ignored ->
                new MigrationConditionStatus(
                    MigrationState.UNKNOWN, "no status reported for this poll"));
      }
    }
  }

  /**
   * One provider call and the polls waiting on it. Each poll waits on its own future, removed when
   * the poll ends, so repeated timeouts leave a hung call with one dependent, not one per poll.
   */
  static final class ProviderCall {

    private final CompletableFuture<Map<String, MigrationConditionStatus>> call;
    private final Set<CompletableFuture<Map<String, MigrationConditionStatus>>> waiters =
        ConcurrentHashMap.newKeySet();

    ProviderCall(final CompletableFuture<Map<String, MigrationConditionStatus>> call) {
      this.call = call;
      call.whenComplete(
          (statuses, error) -> new ArrayList<>(waiters).forEach(w -> finish(w, statuses, error)));
    }

    boolean isDone() {
      return call.isDone();
    }

    CompletableFuture<Map<String, MigrationConditionStatus>> awaitFor(final Duration timeout) {
      final var waiter = new CompletableFuture<Map<String, MigrationConditionStatus>>();
      waiters.add(waiter);
      // A call that finished before this waiter was registered will not complete it.
      if (call.isDone()) {
        call.whenComplete((statuses, error) -> finish(waiter, statuses, error));
      }
      waiter.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
      waiter.whenComplete((statuses, error) -> waiters.remove(waiter));
      return waiter;
    }

    @VisibleForTesting
    int dependentsOfCall() {
      return call.getNumberOfDependents();
    }

    @VisibleForTesting
    int waitingPolls() {
      return waiters.size();
    }

    private static void finish(
        final CompletableFuture<Map<String, MigrationConditionStatus>> waiter,
        final Map<String, MigrationConditionStatus> statuses,
        final Throwable error) {
      if (error != null) {
        waiter.completeExceptionally(error);
      } else {
        waiter.complete(statuses);
      }
    }
  }
}
