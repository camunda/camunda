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
import io.camunda.zeebe.engine.metrics.SecretResolutionMetrics;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionCycleDelayReason;
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
 * the cooldown-aware delay is honored instead). Likewise, a cycle that resolves anything, or that
 * finds nothing but a reference was requested via {@link #wake()} since it last ran, reschedules
 * its next run at the shorter {@code wakeDelay} rather than {@code schedulingInterval}: under a
 * sustained stream of requests this keeps every cycle after the first close to {@code wakeDelay}
 * apart on its own, without ever needing to bring a scheduled cycle forward by cancelling it. A
 * cycle that neither makes progress nor is woken, and has no store waiting out a retry cooldown
 * either, does not jump straight to {@code schedulingInterval}: see {@link #nextIdleBackoff()} for
 * why and how it grows the delay geometrically instead.
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
  private final SecretResolutionMetrics metrics;
  private final Duration schedulingInterval;
  private final Duration retryInitialDelay;
  private final Duration retryMaxDelay;
  private final int retryMaxAttempts;
  private final int retryBackoffFactor;
  private final int batchLimit;
  private final Duration wakeDelay;

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

  /**
   * Set by {@link #wake()}, consulted and cleared at the start of each cycle. Marks that some
   * activation requested a resolution since the last cycle ran, even if that activation's own
   * {@code RESOLUTION_REQUESTED} record is not yet what makes a cycle's collection non-empty (the
   * two are written from different actors and can interleave either way). A cycle that finds this
   * set stays on the fast, {@code wakeDelay} cadence for its next run instead of falling back to
   * {@code schedulingInterval}, so a queue that is momentarily empty between two waves of requests
   * is not mistaken for the scheduler being genuinely idle.
   *
   * <p>Deliberately does not itself bring a pending execution forward: this scheduler's chain
   * already reschedules itself at {@code wakeDelay} whenever a cycle resolves anything, so under
   * the sustained request rate this scheduler exists for, that chain stays on the fast cadence on
   * its own once started, without this scheduler ever needing to cancel a scheduled task to get
   * there.
   */
  private final AtomicBoolean wakePending = new AtomicBoolean();

  /**
   * The delay {@link #nextIdleBackoff()} returned last, or {@code 0} if the ladder has not started
   * since its last reset. Read and written only from the {@link AsyncTaskGroup#SECRET_RESOLUTION}
   * actor, the same discipline as {@link #storeRetryStates} and {@link #collectionCursor}.
   */
  private long idleBackoffMillis;

  private ReadonlyStreamProcessorContext processingContext;
  private InstantSource clock;

  public SecretResolutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SecretStoreRegistry secretStoreRegistry,
      final EngineConfiguration config,
      final SecretResolutionMetrics metrics) {
    secretReferenceState = scheduledTaskStateFactory.get().getSecretReferenceState();
    this.secretStoreRegistry = secretStoreRegistry;
    this.metrics = metrics;
    schedulingInterval = config.getSecretResolutionInterval();
    retryInitialDelay = config.getSecretResolutionRetryInitialDelay();
    retryMaxDelay = config.getSecretResolutionRetryMaxDelay();
    retryMaxAttempts = config.getSecretResolutionRetryMaxAttempts();
    retryBackoffFactor = config.getSecretResolutionRetryBackoffFactor();
    batchLimit = config.getSecretResolutionBatchLimit();
    wakeDelay = config.getSecretResolutionWakeDelay();
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

  /**
   * Marks that a secret reference was just requested, so the next cycle to run stays on the fast
   * {@code wakeDelay} cadence instead of falling back to the full {@code schedulingInterval}.
   * Called from the stream processor once per activation that requested any resolution.
   *
   * <p>Does not schedule or cancel anything itself. This scheduler already reschedules itself at
   * {@code wakeDelay} whenever a cycle resolves at least one reference, so under the sustained
   * request rate this exists for, that alone keeps the chain on the fast cadence; this flag only
   * covers the gap where a cycle's collection happens to find nothing pending because it raced a
   * request that is about to be written, not because the scheduler is genuinely idle.
   */
  public void wake() {
    wakePending.set(true);
  }

  TaskResult resolveSecrets(final TaskResultBuilder resultBuilder) {
    taskScheduled.set(false);
    final boolean woken = wakePending.getAndSet(false);
    final long now = clock.millis();
    boolean taskResultBatchFull = false;
    boolean capped = false;
    Set<String> cooldownSkippedStores = Set.of();
    final var progressMade = new MutableBoolean(false);
    try {
      final CollectedPendingRefs pending = collectPendingByStore(now);
      capped = pending.capped();
      cooldownSkippedStores = pending.cooldownSkippedStores();
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
            // a store failing this way is a bug rather than one of the modelled outcomes, and it
            // would otherwise be log-only: no outcome is counted for these references, so every
            // rate built on the outcome counter would read as "nothing is happening". Counted on
            // its own meter, per cycle, because these references stay pending — see
            // SecretResolutionMetrics#error. Deliberately only what this catch covers: an Error is
            // not a cycle the scheduler carried on from, and is visible on the duration timer
            // (result=ERROR) and in the task failing instead.
            metrics.error(entry.getKey());
          }
        }
      }
    } finally {
      final Duration delay;
      if (taskResultBatchFull || (capped && progressMade.get())) {
        // more pending refs than this cycle's batch cap allowed it to take; keep draining
        delay = Duration.ZERO;
        idleBackoffMillis = 0;
        metrics.cycleDelay(SecretResolutionCycleDelayReason.DRAINING, delay);
      } else if ((progressMade.get() || woken) && cooldownSkippedStores.isEmpty()) {
        // either this cycle resolved something, or one was requested since the last cycle ran and
        // may not have been visible to this cycle's collection yet; either way, staying on the fast
        // cadence is what lets a continuous stream of requests be resolved close to as they arrive,
        // without ever cancelling and replacing a scheduled cycle to get there. Gated on no store
        // cooling down: a wake this cycle cannot be served by a store that is already known to be
        // unavailable, so the fast cadence would just re-scan the same backlog for nothing. The
        // cooldown deadline computed below is what retrying earlier could actually help with.
        delay = wakeDelay;
        idleBackoffMillis = 0;
        metrics.cycleDelay(SecretResolutionCycleDelayReason.WAKE, delay);
      } else {
        delay = computeNextDelay(now);
      }
      scheduleNext(delay);
    }
    return resultBuilder.build();
  }

  /**
   * Returns the delay until the next execution, for a cycle that made no progress and was not
   * woken. If any store is in cooldown with a retry deadline sooner than {@code
   * schedulingInterval}, returns that deadline so the backoff is honored exactly: retrying earlier
   * cannot succeed (collection skips a cooling-down store outright, see {@link
   * #collectPendingByStore}) and there is nothing else this cycle is waiting on that retrying later
   * would help. Otherwise nothing is known to wait for, and {@link #nextIdleBackoff()} governs the
   * delay instead of jumping straight to {@code schedulingInterval}.
   */
  private Duration computeNextDelay(final long now) {
    if (storeRetryStates.isEmpty()) {
      final var delay = nextIdleBackoff();
      metrics.cycleDelay(SecretResolutionCycleDelayReason.IDLE_BACKOFF, delay);
      return delay;
    }
    final long earliestRetryAt =
        storeRetryStates.values().stream()
            .mapToLong(StoreRetryState::nextAttemptAt)
            .min()
            .getAsLong();
    final long millisUntilRetry = earliestRetryAt - now;
    final var delay =
        millisUntilRetry < schedulingInterval.toMillis()
            ? Duration.ofMillis(Math.max(0, millisUntilRetry))
            : schedulingInterval;
    // a cooldown deadline is a known wait, not an idle miss, so reset the ladder here too: the
    // reference that unblocks once the cooldown ends should not have to wait out steps the ladder
    // climbed before the store went into cooldown
    idleBackoffMillis = 0;
    metrics.cycleDelay(SecretResolutionCycleDelayReason.RETRY_COOLDOWN, delay);
    return delay;
  }

  /**
   * Grows the delay before the next cycle geometrically from {@code wakeDelay} up to {@code
   * schedulingInterval} instead of jumping straight there, so one cycle that happens to find
   * nothing does not cost whichever reference arrives next a full {@code schedulingInterval} wait.
   * Under a request rate high enough that most {@code wakeDelay}-sized windows are empty by chance
   * alone, jumping straight to {@code schedulingInterval} after the first such miss would leave the
   * scheduler asleep for nearly all wall-clock time; this keeps most of a request's wait within a
   * few doublings instead, while a genuinely idle scheduler still reaches {@code
   * schedulingInterval} after enough consecutive misses.
   *
   * <p>{@code wakeDelay} of zero is accepted elsewhere and would never grow if doubled from zero,
   * so the first step is floored at 1ms; a {@code wakeDelay} configured larger than {@code
   * schedulingInterval} is clamped the same way, so the first step never overshoots the cap it is
   * meant to climb towards.
   */
  private Duration nextIdleBackoff() {
    idleBackoffMillis =
        idleBackoffMillis == 0
            ? Math.min(schedulingInterval.toMillis(), Math.max(wakeDelay.toMillis(), 1))
            : Math.min(schedulingInterval.toMillis(), idleBackoffMillis * 2);
    return Duration.ofMillis(idleBackoffMillis);
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

    // a pending reference is keyed by the store ID its record carried, and every reference carries
    // the default store ID, which startup validation guarantees is configured. An ID naming no
    // configured store is therefore failed rather than mapped onto the default: mapping one ID onto
    // another is what let this path and job activation disagree about an empty ID (see #59432).
    final var store = secretStoreRegistry.getStores().get(storeId);
    if (store == null) {
      LOG.warn(
          "Secret store '{}' is not configured — failing {} pending secret refs",
          storeId,
          refs.size());
      if (appendStoreUnavailableFails(resultBuilder, storeId, refs, progressMade) < refs.size()) {
        // batch full; refs not yet appended remain pending and will be retried next cycle —
        // leave retry state untouched instead of resetting it out from under them
        return false;
      }
      storeRetryStates.remove(storeId);
      return true;
    }

    try {
      final var results = metrics.recordResolution(storeId, () -> store.resolveFromStore(refs));
      for (final var entry : results.entrySet()) {
        final String ref = entry.getKey();
        final boolean appended;
        // only a reference whose command was appended has reached a terminal outcome; one that did
        // not fit stays pending and is counted when a later cycle appends it. Appending is the
        // earliest point the outcome is known, but not the point the reference stops being pending
        // — that is the RESOLUTION_COMPLETED event — so a cycle running before the command is
        // processed resolves the reference again and counts it again, even though the duplicate
        // command is then rejected. The over-count is documented on the meter; counting on the
        // applied event instead would lose the store error code, which the record does not carry.
        switch (entry.getValue()) {
          // the value stays in the store and is never touched here, so the resolution record
          // carries no secret
          case final SecretResolutionResult.Resolved ignored -> {
            appended = appendResolutionComplete(resultBuilder, storeId, ref);
            if (appended) {
              metrics.resolved(storeId);
            }
          }
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
            if (appended) {
              // the metric keeps the code the stream record loses
              metrics.failed(storeId, code);
            }
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
        if (appendStoreUnavailableFails(resultBuilder, storeId, refs, progressMade) < refs.size()) {
          // batch full; refs not yet appended remain pending. Leave retry state untouched so
          // the next cycle still sees attempts >= retryMaxAttempts and continues failing them,
          // instead of resetting to 0 and re-entering the backoff branch below.
          return false;
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

  /**
   * Fails as many references of an unusable store as the result still has room for, counting the
   * outcome for each one it appended. The refs it did not reach stay pending and are failed by the
   * next cycle: the caller leaves the retry state alone in that case, so neither a store that is
   * not configured nor one whose retries ran out re-enters the backoff ladder on their account.
   *
   * @return how many references the result took, which is fewer than {@code refs} only when the
   *     batch filled up
   */
  private int appendStoreUnavailableFails(
      final TaskResultBuilder resultBuilder,
      final String storeId,
      final Set<String> refs,
      final MutableBoolean progressMade) {
    int appended = 0;
    for (final String ref : refs) {
      if (!appendResolutionFail(resultBuilder, storeId, ref, ResolutionState.STORE_UNAVAILABLE)) {
        break;
      }
      appended++;
      progressMade.set(true);
    }
    metrics.storeUnavailable(storeId, appended);
    return appended;
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
