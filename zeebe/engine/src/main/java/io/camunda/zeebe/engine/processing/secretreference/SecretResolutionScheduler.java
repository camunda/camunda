/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically reads up to {@code batchLimit} pending secret references from state per cycle,
 * resolves them in batches (one batch per store) and writes {@link
 * SecretReferenceIntent#RESOLUTION_COMPLETE} or {@link SecretReferenceIntent#RESOLUTION_FAIL}
 * commands. The store holds what it reads, which is what makes a resolved value available to the
 * activation that requested the resolution. Any refs beyond the cap stay pending and are retried in
 * the next cycle — which is scheduled immediately ({@link Duration#ZERO}) instead of waiting the
 * normal {@code schedulingInterval} (unless a store is currently in retry cooldown, in which case
 * the cooldown-aware delay is honored instead).
 *
 * <p>Resolution goes through {@link
 * io.camunda.secretstore.LocallyCachedSecretStore#resolveFromStore} rather than the cache-first
 * {@code resolve}, for the reason given there.
 *
 * <p>Two distinct failure modes:
 *
 * <ul>
 *   <li>{@link SecretResolutionResult.Failed} — permanent per-secret error (NOT_FOUND,
 *       ACCESS_DENIED, INVALID_REF): write {@code RESOLUTION_FAIL} immediately, no retry, no cache
 *       write.
 *   <li>{@link SecretStoreUnavailableException} — transient store-level failure: retry the whole
 *       store with exponential backoff. After {@code retryMaxAttempts} failures, write {@code
 *       RESOLUTION_FAIL} for all pending refs in that store.
 * </ul>
 *
 * <p>Retry state ({@code attempts} + {@code nextAttemptAt} per storeId) is in-memory only and
 * intentionally resets on broker restart / partition failover.
 *
 * <p>Runs on {@link AsyncTaskGroup#SECRET_RESOLUTION} (IO-bound) so the stream processor is never
 * blocked by store IO.
 *
 * <p>A scheduled task survives a stream processor pause (the platform re-submits it until
 * processing resumes), so this scheduler keeps exactly one scheduling chain alive across
 * pause/resume cycles via the {@code shouldReschedule} flag and the {@code taskScheduled} guard.
 */
public final class SecretResolutionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(SecretResolutionScheduler.class);

  private final SecretReferenceState secretReferenceState;
  private final SecretStoreRegistry secretStoreRegistry;
  private final Duration schedulingInterval;
  private final Duration retryInitialDelay;
  private final Duration retryMaxDelay;
  private final int retryMaxAttempts;
  private final int retryBackoffFactor;
  private final int batchLimit;

  private final Map<String, StoreRetryState> storeRetryStates = new HashMap<>();

  /** Carries the resume point for {@link #collectPendingByStore} fairly across cycles. */
  private SecretReferenceState.PendingRefCursor collectionCursor;

  /**
   * Whether the scheduler may reschedule itself. Controlled by the stream processor's lifecycle
   * events, e.g. {@link #onPaused()} and {@link #onResumed()}.
   */
  private volatile boolean shouldReschedule;

  /**
   * Guards against parallel scheduling chains: a scheduled task survives a pause (the platform
   * re-submits it until processing resumes), so {@link #onResumed()} must not start a second chain
   * while one is still pending.
   */
  private final AtomicBoolean taskScheduled = new AtomicBoolean();

  private ReadonlyStreamProcessorContext processingContext;
  private InstantSource clock;

  public SecretResolutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SecretStoreRegistry secretStoreRegistry,
      final EngineConfiguration config) {
    secretReferenceState = scheduledTaskStateFactory.get().getSecretReferenceState();
    this.secretStoreRegistry = secretStoreRegistry;
    schedulingInterval = config.getSecretResolutionInterval();
    retryInitialDelay = config.getSecretResolutionRetryInitialDelay();
    retryMaxDelay = config.getSecretResolutionRetryMaxDelay();
    retryMaxAttempts = config.getSecretResolutionRetryMaxAttempts();
    retryBackoffFactor = config.getSecretResolutionRetryBackoffFactor();
    batchLimit = config.getSecretResolutionBatchLimit();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    clock = context.getClock();
    shouldReschedule = true;
    scheduleNext(schedulingInterval);
  }

  @Override
  public void onResumed() {
    shouldReschedule = true;
    // Schedule immediately; resolveSecrets computes the real backoff-aware delay on the
    // SECRET_RESOLUTION actor. Calling computeNextDelay() here would read storeRetryStates from the
    // stream-processor actor concurrently with the async cycle's mutations (a data race).
    scheduleNext(Duration.ZERO);
  }

  @Override
  public void onPaused() {
    shouldReschedule = false;
  }

  @Override
  public void onClose() {
    shouldReschedule = false;
  }

  @Override
  public void onFailed() {
    shouldReschedule = false;
  }

  TaskResult resolveSecrets(final TaskResultBuilder resultBuilder) {
    taskScheduled.set(false);
    final long now = clock.millis();
    boolean taskResultBatchFull = false;
    boolean capped = false;
    final var progressMade = new MutableBoolean(false);
    try {
      final CollectedPendingRefs pending = collectPendingByStore(now);
      capped = pending.capped();
      final Map<String, Set<String>> pendingByStore = pending.refsByStore();
      // Prune retry state for stores confirmed to have zero pending refs this cycle. Only
      // trustworthy when the scan was both uncapped AND didn't skip any store for cooldown —
      // either condition means a store with pending refs can be missing from pendingByStore for a
      // reason OTHER than "it has no pending refs", and retainAll would wipe its backoff state as
      // if it had none, resetting its attempt counter and letting it starve indefinitely without
      // ever reaching retryMaxAttempts.
      if (!pending.capped() && pending.cooldownSkippedStores().isEmpty()) {
        storeRetryStates.keySet().retainAll(pendingByStore.keySet());
      }
      if (!pendingByStore.isEmpty()) {
        for (final var entry : pendingByStore.entrySet()) {
          try {
            if (!resolveStore(entry.getKey(), entry.getValue(), resultBuilder, now, progressMade)) {
              // the task result batch is full; stop appending more commands this cycle
              taskResultBatchFull = true;
              break;
            }
          } catch (final RuntimeException e) {
            LOG.error(
                "Unexpected error while resolving secrets from store '{}'; "
                    + "{} secret reference(s) remain pending and will be retried in the next cycle",
                entry.getKey(),
                entry.getValue().size(),
                e);
          }
        }
      }
    } finally {
      final boolean immediateReschedule = taskResultBatchFull || (capped && progressMade.get());
      scheduleNext(immediateReschedule ? Duration.ZERO : computeNextDelay(now));
    }
    return resultBuilder.build();
  }

  /**
   * Returns the delay until the next execution. If any store is in cooldown with a retry deadline
   * sooner than {@code schedulingInterval}, returns the shorter duration so backoff is honored.
   */
  private Duration computeNextDelay(final long now) {
    if (storeRetryStates.isEmpty()) {
      return schedulingInterval;
    }
    final long earliestRetryAt =
        storeRetryStates.values().stream()
            .mapToLong(StoreRetryState::nextAttemptAt)
            .min()
            .getAsLong();
    final long millisUntilRetry = earliestRetryAt - now;
    if (millisUntilRetry < schedulingInterval.toMillis()) {
      return Duration.ofMillis(Math.max(0, millisUntilRetry));
    }
    return schedulingInterval;
  }

  private CollectedPendingRefs collectPendingByStore(final long now) {
    final Map<String, Set<String>> pendingByStore = new LinkedHashMap<>();
    final Set<String> cooldownSkippedStores = new LinkedHashSet<>();
    final var counter = new MutableInteger(0);
    final var lastVisited =
        secretReferenceState.visitPendingSecretReferences(
            collectionCursor,
            (storeId, secretRef) -> {
              final StoreRetryState retryState =
                  storeRetryStates.getOrDefault(storeId, StoreRetryState.INITIAL);
              if (now < retryState.nextAttemptAt()) {
                // store is cooling down: don't let its refs consume cap budget that a healthy
                // store could use instead — resolveStore would just skip them anyway
                cooldownSkippedStores.add(storeId);
                return true;
              }
              if (counter.get() >= batchLimit) {
                return false;
              }
              counter.increment();
              pendingByStore.computeIfAbsent(storeId, k -> new LinkedHashSet<>()).add(secretRef);
              return true;
            });
    collectionCursor = lastVisited; // null once a full scan completes; resume point otherwise
    return new CollectedPendingRefs(pendingByStore, lastVisited != null, cooldownSkippedStores);
  }

  /**
   * Resolves the pending refs for a single store, appending {@code RESOLUTION_COMPLETE}/{@code
   * RESOLUTION_FAIL} commands as results become available.
   *
   * <p>The caller guarantees {@code storeId} is not currently in retry cooldown — {@link
   * #collectPendingByStore} filters those out before they ever reach here.
   *
   * @return {@code false} if the task result batch filled up and the cycle must stop immediately —
   *     no further stores should be processed this cycle; {@code true} otherwise
   */
  private boolean resolveStore(
      final String storeId,
      final Set<String> refs,
      final TaskResultBuilder resultBuilder,
      final long now,
      final MutableBoolean progressMade) {
    final StoreRetryState retryState =
        storeRetryStates.getOrDefault(storeId, StoreRetryState.INITIAL);

    // a pending reference is keyed by the store ID its record carries, which for a
    // camunda.secrets.<name> reference is empty and addresses no store here (see #59432)
    final var store = secretStoreRegistry.getStores().get(storeId);
    if (store == null) {
      LOG.warn(
          "Secret store '{}' is not configured — failing {} pending secret refs",
          storeId,
          refs.size());
      for (final String ref : refs) {
        if (!appendResolutionFail(resultBuilder, storeId, ref, ResolutionState.STORE_UNAVAILABLE)) {
          // batch full; refs not yet appended remain pending and will be retried next cycle —
          // leave retry state untouched instead of resetting it out from under them
          return false;
        }
        progressMade.set(true);
      }
      storeRetryStates.remove(storeId);
      return true;
    }

    try {
      final var results = store.resolveFromStore(refs);
      for (final var entry : results.entrySet()) {
        final String ref = entry.getKey();
        final boolean appended;
        switch (entry.getValue()) {
          // the value stays in the store and is never touched here, so the resolution record
          // carries no secret
          case final SecretResolutionResult.Resolved ignored ->
              appended = appendResolutionComplete(resultBuilder, storeId, ref);
          case SecretResolutionResult.Failed(
                  final var code,
                  final var message,
                  final var cause) -> {
            LOG.warn(
                "Secret '{}' in secret store '{}' failed permanently: {} — {}",
                ref,
                storeId,
                code,
                message,
                cause);
            // All permanent per-secret failures (NOT_FOUND/ACCESS_DENIED/INVALID_REF) map to
            // NOT_FOUND for now; ResolutionState has no finer per-secret states yet (#57855
            // will refine).
            appended = appendResolutionFail(resultBuilder, storeId, ref, ResolutionState.NOT_FOUND);
          }
        }
        if (!appended) {
          // batch full; refs not yet appended remain pending and will be retried next cycle —
          // leave retry state untouched instead of resetting it out from under them
          return false;
        }
        progressMade.set(true);
      }
      storeRetryStates.remove(storeId);
      return true;
    } catch (final SecretStoreUnavailableException e) {
      final int nextAttempts = retryState.attempts() + 1;
      if (nextAttempts >= retryMaxAttempts) {
        LOG.warn(
            "Secret store '{}' unavailable after {}/{} attempts — failing {} pending refs: {}",
            storeId,
            nextAttempts,
            retryMaxAttempts,
            refs.size(),
            e.getMessage());
        for (final String ref : refs) {
          if (!appendResolutionFail(
              resultBuilder, storeId, ref, ResolutionState.STORE_UNAVAILABLE)) {
            // batch full; refs not yet appended remain pending. Leave retry state untouched so
            // the next cycle still sees attempts >= retryMaxAttempts and continues failing them,
            // instead of resetting to 0 and re-entering the backoff branch below.
            return false;
          }
          progressMade.set(true);
        }
        storeRetryStates.remove(storeId);
      } else {
        final Duration backoff = calculateBackoff(nextAttempts);
        LOG.warn(
            "Secret store '{}' unavailable (attempt {}/{}), retrying in {}: {}",
            storeId,
            nextAttempts,
            retryMaxAttempts,
            backoff,
            e.getMessage());
        storeRetryStates.put(storeId, new StoreRetryState(nextAttempts, now + backoff.toMillis()));
      }
      return true;
    }
  }

  private boolean appendResolutionComplete(
      final TaskResultBuilder resultBuilder, final String storeId, final String secretRef) {
    final var record = new SecretReferenceRecord();
    record
        .setStoreId(storeId)
        .setSecretReference(secretRef)
        .setResolutionState(ResolutionState.SUCCESS);
    return resultBuilder.appendCommandRecord(SecretReferenceIntent.RESOLUTION_COMPLETE, record);
  }

  private boolean appendResolutionFail(
      final TaskResultBuilder resultBuilder,
      final String storeId,
      final String secretRef,
      final ResolutionState resolutionState) {
    final var record = new SecretReferenceRecord();
    record.setStoreId(storeId).setSecretReference(secretRef).setResolutionState(resolutionState);
    return resultBuilder.appendCommandRecord(SecretReferenceIntent.RESOLUTION_FAIL, record);
  }

  Duration calculateBackoff(final int attempts) {
    Duration delay = retryInitialDelay;
    for (int i = 1; i < attempts; i++) {
      delay = delay.multipliedBy(retryBackoffFactor);
      if (delay.compareTo(retryMaxDelay) >= 0) {
        return retryMaxDelay;
      }
    }
    return delay;
  }

  private void scheduleNext(final Duration delay) {
    if (!shouldReschedule) {
      return;
    }
    if (!taskScheduled.compareAndSet(false, true)) {
      // a task is already scheduled or parked; it will continue the chain itself
      return;
    }
    processingContext
        .getScheduleService()
        .runDelayedAsync(delay, this::resolveSecrets, AsyncTaskGroup.SECRET_RESOLUTION);
  }

  /** In-memory retry state for a single store. Resets on broker restart (by design). */
  record StoreRetryState(int attempts, long nextAttemptAt) {
    static final StoreRetryState INITIAL = new StoreRetryState(0, 0L);
  }

  /**
   * @param capped whether more pending refs exist in state than {@code batchLimit} allowed
   *     collecting this cycle — the scheduler should reschedule immediately rather than wait the
   *     normal {@code schedulingInterval}
   * @param cooldownSkippedStores stores whose pending refs were excluded from {@code refsByStore}
   *     this cycle because the store is currently in retry cooldown
   */
  private record CollectedPendingRefs(
      Map<String, Set<String>> refsByStore, boolean capped, Set<String> cooldownSkippedStores) {}
}
