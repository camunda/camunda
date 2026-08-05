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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically reads all pending secret references from state, resolves them in batches (one batch
 * per store) and writes {@link SecretReferenceIntent#RESOLUTION_COMPLETE} or {@link
 * SecretReferenceIntent#RESOLUTION_FAIL} commands. The store holds what it reads, which is what
 * makes a resolved value available to the activation that requested the resolution.
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

  private final Map<String, StoreRetryState> storeRetryStates = new HashMap<>();

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
    try {
      final Map<String, Set<String>> pendingByStore = collectPendingByStore();
      // prune retry state for stores that no longer have pending refs
      storeRetryStates.keySet().retainAll(pendingByStore.keySet());
      if (!pendingByStore.isEmpty()) {
        for (final var entry : pendingByStore.entrySet()) {
          try {
            resolveStore(entry.getKey(), entry.getValue(), resultBuilder, now);
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
      scheduleNext(computeNextDelay(now));
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

  private Map<String, Set<String>> collectPendingByStore() {
    final Map<String, Set<String>> pendingByStore = new LinkedHashMap<>();
    secretReferenceState.visitPendingSecretReferences(
        (storeId, secretRef) ->
            pendingByStore.computeIfAbsent(storeId, k -> new LinkedHashSet<>()).add(secretRef));
    return pendingByStore;
  }

  private void resolveStore(
      final String storeId,
      final Set<String> refs,
      final TaskResultBuilder resultBuilder,
      final long now) {
    final StoreRetryState retryState =
        storeRetryStates.getOrDefault(storeId, StoreRetryState.INITIAL);

    if (now < retryState.nextAttemptAt()) {
      LOG.trace(
          "Secret store '{}' in cooldown until {}ms, skipping {} pending refs",
          storeId,
          retryState.nextAttemptAt(),
          refs.size());
      return;
    }

    // a pending reference is keyed by the store ID its record carries, which for a
    // camunda.secrets.<name> reference is empty and addresses no store here (see #59432)
    final var store = secretStoreRegistry.getStores().get(storeId);
    if (store == null) {
      LOG.warn(
          "Secret store '{}' is not configured — failing {} pending secret refs",
          storeId,
          refs.size());
      refs.forEach(
          ref ->
              appendResolutionFail(resultBuilder, storeId, ref, ResolutionState.STORE_UNAVAILABLE));
      storeRetryStates.remove(storeId);
      return;
    }

    try {
      final var results = store.resolveFromStore(refs);
      results.forEach(
          (ref, result) -> {
            switch (result) {
              // the value stays in the store and is never touched here, so the resolution record
              // carries no secret
              case final SecretResolutionResult.Resolved ignored ->
                  appendResolutionComplete(resultBuilder, storeId, ref);
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
                // All permanent per-secret failures (NOT_FOUND/ACCESS_DENIED/INVALID_REF) map
                // to NOT_FOUND for now; ResolutionState has no finer per-secret states yet
                // (#57855 will refine).
                appendResolutionFail(resultBuilder, storeId, ref, ResolutionState.NOT_FOUND);
              }
            }
          });
      storeRetryStates.remove(storeId);
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
        refs.forEach(
            ref ->
                appendResolutionFail(
                    resultBuilder, storeId, ref, ResolutionState.STORE_UNAVAILABLE));
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
    }
  }

  private void appendResolutionComplete(
      final TaskResultBuilder resultBuilder, final String storeId, final String secretRef) {
    final var record = new SecretReferenceRecord();
    record
        .setStoreId(storeId)
        .setSecretReference(secretRef)
        .setResolutionState(ResolutionState.SUCCESS);
    resultBuilder.appendCommandRecord(SecretReferenceIntent.RESOLUTION_COMPLETE, record);
  }

  private void appendResolutionFail(
      final TaskResultBuilder resultBuilder,
      final String storeId,
      final String secretRef,
      final ResolutionState resolutionState) {
    final var record = new SecretReferenceRecord();
    record.setStoreId(storeId).setSecretReference(secretRef).setResolutionState(resolutionState);
    resultBuilder.appendCommandRecord(SecretReferenceIntent.RESOLUTION_FAIL, record);
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
}
