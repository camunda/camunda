/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionCallResult;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionCycleDelayReason;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionKeyNames;
import io.camunda.zeebe.engine.metrics.SecretResolutionMetricsDoc.SecretResolutionOutcome;
import io.camunda.zeebe.util.micrometer.BoundedMeterCache;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Records how long resolving secrets from a store takes, what resolving each secret reference ended
 * up as, and the cycles a store failed unexpectedly in. Without these, a store that fails,
 * throttles, or denies access is visible only in the broker log, so there is nothing to alert on.
 *
 * <p>The per-reference outcomes and the per-cycle errors are separate meters rather than values on
 * one counter, so that neither can be added to the other by a query that aggregates across a tag.
 *
 * <p>Every meter is tagged by store ID, which is not known up front: it comes from the pending
 * secret references in state, not from configuration, and the engine resolves references for store
 * IDs it has no configured store for. The meters are therefore held in {@link BoundedMeterCache}s
 * keyed by store ID. The domain is small today — the default store ID every {@code
 * camunda.secrets.<name>} reference carries, plus {@link SecretResolutionKeyNames#NO_STORE} for a
 * reference written before that ID existed — but once store selection is wired to the engine (<a
 * href="https://github.com/camunda/camunda/issues/56563">#56563</a>) a store ID becomes
 * process-author input, and the bound is what keeps that from growing the registry without limit.
 */
public final class SecretResolutionMetrics {

  private final MeterRegistry registry;
  private final Map<SecretResolutionCallResult, BoundedMeterCache<Timer>> resolutionTimers =
      new EnumMap<>(SecretResolutionCallResult.class);
  private final Map<SecretResolutionOutcome, BoundedMeterCache<Counter>> outcomeCounters =
      new EnumMap<>(SecretResolutionOutcome.class);
  private final BoundedMeterCache<Counter> cycleErrors;
  private final Map<SecretResolutionCycleDelayReason, Timer> cycleDelayTimers =
      new EnumMap<>(SecretResolutionCycleDelayReason.class);

  public SecretResolutionMetrics(final MeterRegistry registry) {
    this.registry = registry;
    for (final var callResult : SecretResolutionCallResult.values()) {
      resolutionTimers.put(callResult, timerCache(registry, callResult));
    }
    for (final var outcome : SecretResolutionOutcome.values()) {
      outcomeCounters.put(outcome, counterCache(registry, outcome));
    }
    cycleErrors = cycleErrorCache(registry);
    for (final var reason : SecretResolutionCycleDelayReason.values()) {
      cycleDelayTimers.put(reason, cycleDelayTimer(registry, reason));
    }
  }

  /**
   * Runs one batch resolution call against the given store and records how long it took, split by
   * how the call itself ended. Only the call is measured, so the command appends that follow a
   * successful one stay out of the latency.
   */
  public <T> T recordResolution(final String storeId, final Supplier<T> storeCall) {
    final var sample = Timer.start(registry);
    // anything that leaves this method other than a value or a SecretStoreUnavailableException is
    // an unmodelled failure, an Error included. Defaulting to ERROR and setting RETURNED only on
    // the path that returns a value covers those without catching Throwable, which would otherwise
    // time a failed call under the success bucket.
    var callResult = SecretResolutionCallResult.ERROR;
    try {
      final T result = storeCall.get();
      callResult = SecretResolutionCallResult.RETURNED;
      return result;
    } catch (final SecretStoreUnavailableException e) {
      callResult = SecretResolutionCallResult.STORE_UNAVAILABLE;
      throw e;
    } finally {
      sample.stop(resolutionTimers.get(callResult).get(storeTag(storeId)));
    }
  }

  /** Records that one secret reference was resolved to a value. */
  public void resolved(final String storeId) {
    outcomeCounter(storeId, SecretResolutionOutcome.RESOLVED).increment();
  }

  /** Records that one secret reference failed permanently with the store's own error code. */
  public void failed(final String storeId, final SecretErrorCode code) {
    outcomeCounter(storeId, SecretResolutionOutcome.from(code)).increment();
  }

  /**
   * Records that {@code referenceCount} references failed because their store is not configured, or
   * could not be reached with no retry attempt left.
   */
  public void storeUnavailable(final String storeId, final int referenceCount) {
    if (referenceCount <= 0) {
      // nothing happened, so registering the series would only add an always-zero line
      return;
    }
    outcomeCounter(storeId, SecretResolutionOutcome.STORE_UNAVAILABLE).increment(referenceCount);
  }

  /**
   * Records one resolution cycle in which a store failed in a way the engine does not model. The
   * references it left pending are retried, so this counts cycles rather than references — counting
   * the references instead would scale the series with the pending backlog and with how often the
   * cycle runs, and neither is a quantity anyone can alert on.
   *
   * <p>That unit is why this is its own meter rather than another value on the outcome counter: a
   * counter mixing cycles and references cannot be summed or divided across its {@code result} tag,
   * so every rate and failure ratio built on it would quietly add the two together.
   */
  public void error(final String storeId) {
    cycleErrors.get(storeTag(storeId)).increment();
  }

  /** Records the delay a resolution cycle chose for the next one, tagged by why. */
  public void cycleDelay(final SecretResolutionCycleDelayReason reason, final Duration delay) {
    cycleDelayTimers.get(reason).record(delay);
  }

  private Counter outcomeCounter(final String storeId, final SecretResolutionOutcome outcome) {
    return outcomeCounters.get(outcome).get(storeTag(storeId));
  }

  private static BoundedMeterCache<Timer> timerCache(
      final MeterRegistry registry, final SecretResolutionCallResult callResult) {
    final var provider =
        MicrometerUtil.buildTimer(SecretResolutionMetricsDoc.RESOLUTION_DURATION)
            .tag(SecretResolutionKeyNames.RESULT.asString(), callResult.name())
            .withRegistry(registry);
    return BoundedMeterCache.of(registry, provider, SecretResolutionKeyNames.STORE);
  }

  private static BoundedMeterCache<Counter> counterCache(
      final MeterRegistry registry, final SecretResolutionOutcome outcome) {
    final var meterDoc = SecretResolutionMetricsDoc.RESOLUTION_OUTCOME;
    final var provider =
        Counter.builder(meterDoc.getName())
            .description(meterDoc.getDescription())
            .tag(SecretResolutionKeyNames.RESULT.asString(), outcome.name())
            .withRegistry(registry);
    return BoundedMeterCache.of(registry, provider, SecretResolutionKeyNames.STORE);
  }

  private static BoundedMeterCache<Counter> cycleErrorCache(final MeterRegistry registry) {
    final var meterDoc = SecretResolutionMetricsDoc.RESOLUTION_CYCLE_ERROR;
    final var provider =
        Counter.builder(meterDoc.getName())
            .description(meterDoc.getDescription())
            .withRegistry(registry);
    return BoundedMeterCache.of(registry, provider, SecretResolutionKeyNames.STORE);
  }

  /**
   * Not a {@link BoundedMeterCache}: unlike the store-keyed meters above, {@link
   * SecretResolutionCycleDelayReason} is a small, fixed domain the engine defines, not one that
   * grows with data the engine only later learns about, so there is no cardinality to bound.
   */
  private static Timer cycleDelayTimer(
      final MeterRegistry registry, final SecretResolutionCycleDelayReason reason) {
    return MicrometerUtil.buildTimer(SecretResolutionMetricsDoc.CYCLE_DELAY)
        .tag(SecretResolutionKeyNames.RESULT.asString(), reason.name())
        .register(registry);
  }

  /**
   * A secret reference written before #59432 carries no store ID, so the raw value is the empty
   * string. An empty tag value is indistinguishable from an absent one on a dashboard, so it gets a
   * sentinel the same way {@code PartitionKeyNames.noPartition()} does.
   */
  private static String storeTag(final String storeId) {
    return storeId.isBlank() ? SecretResolutionKeyNames.NO_STORE : storeId;
  }
}
