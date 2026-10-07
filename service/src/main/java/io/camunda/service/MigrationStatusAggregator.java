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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
 * <p>Providers make blocking calls (secondary storage, brokers) that can hang for tens of seconds,
 * so they run on a dedicated executor and never on the shared REST API executor: a stuck poll can
 * neither occupy nor wait behind the threads that serve every other request. Nobody waits on that
 * executor from one of its own threads either, which is what let a one-thread pool deadlock.
 */
public class MigrationStatusAggregator implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationStatusAggregator.class);

  /**
   * How long one poll waits for a single provider. It sits above the 5s budget that the
   * broker-facing providers give themselves, so their per-tenant partial answers are not discarded
   * by this timer racing them.
   */
  static final Duration DEFAULT_PROVIDER_TIMEOUT = Duration.ofSeconds(7);

  private final List<MigrationStatusProvider> providers;
  private final Duration providerTimeout;
  private final Executor executor;
  private final boolean ownsExecutor;
  private final Set<String> knownPhysicalTenantIds = ConcurrentHashMap.newKeySet();

  private final Object pollLock = new Object();
  // The task last started for each provider, by index. While one is still running, polls wait on
  // it instead of starting another, so a hung provider holds one thread and not one per poll.
  private final List<CompletableFuture<Map<String, MigrationConditionStatus>>> runningTasks;
  private CompletableFuture<Map<String, Map<String, MigrationConditionStatus>>> inFlightPoll;

  public MigrationStatusAggregator(final List<MigrationStatusProvider> providers) {
    this(providers, DEFAULT_PROVIDER_TIMEOUT);
  }

  public MigrationStatusAggregator(
      final List<MigrationStatusProvider> providers, final Duration providerTimeout) {
    this(providers, providerTimeout, newProviderExecutor(), true);
  }

  public MigrationStatusAggregator(
      final List<MigrationStatusProvider> providers,
      final Duration providerTimeout,
      final Executor executor) {
    this(providers, providerTimeout, executor, false);
  }

  private MigrationStatusAggregator(
      final List<MigrationStatusProvider> providers,
      final Duration providerTimeout,
      final Executor executor,
      final boolean ownsExecutor) {
    this.providers = List.copyOf(providers);
    this.providerTimeout = providerTimeout;
    this.executor = executor;
    this.ownsExecutor = ownsExecutor;
    runningTasks = new ArrayList<>();
    providers.forEach(provider -> runningTasks.add(null));
  }

  /** Blocks the calling thread, for at most about the provider timeout, until the poll is done. */
  public Map<String, Map<String, MigrationConditionStatus>> aggregate() {
    return aggregateAsync().join();
  }

  /**
   * Polls every provider without holding a thread while it waits. A provider that does not answer
   * within the provider timeout is reported as {@code UNKNOWN}. Callers that arrive while a poll is
   * in flight share it, instead of starting another one behind it.
   *
   * <p>A provider call that outlives its poll is not started again by the next one; that poll waits
   * on the same call. Its answer can therefore be as old as the call, which a slow provider makes
   * as long as that provider takes to answer. Providers only ever report progress, so an older
   * answer errs towards "not migrated yet".
   *
   * <p>Returns a failed future, and does not throw, once the executor no longer accepts work.
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
      // A copy, so a caller completing or cancelling its future cannot corrupt the shared poll.
      return inFlightPoll.copy();
    }
  }

  @Override
  public void close() {
    if (ownsExecutor) {
      ((ExecutorService) executor).shutdown();
    }
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

    // The merge is cheap and must not queue behind a provider that holds the executor's threads, so
    // it runs on the default async pool. It also keeps the shared timer thread free: the last
    // provider may complete there.
    return CompletableFuture.allOf(pendingStatusesByProvider.toArray(CompletableFuture<?>[]::new))
        .thenApplyAsync(ignored -> merge(pendingStatusesByProvider, conditionNames));
  }

  private CompletableFuture<Map<String, MigrationConditionStatus>> pollProvider(final int index) {
    final var provider = providers.get(index);
    var task = runningTasks.get(index);
    if (task == null || task.isDone()) {
      task = CompletableFuture.supplyAsync(() -> safeGetMigrationStatus(provider), executor);
      runningTasks.set(index, task);
    }
    // The timeout applies to a copy, so giving up on a poll does not complete the task itself.
    return task.copy()
        .orTimeout(providerTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .exceptionally(
            error -> {
              if (error instanceof TimeoutException
                  || error.getCause() instanceof TimeoutException) {
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
}
