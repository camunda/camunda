/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.InMemorySecretCache;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class SecretResolutionSchedulerTest {

  /**
   * The only store ID an operator can configure — startup validation rejects any other — and so the
   * one every {@code camunda.secrets.<name>} reference carries. It used to be empty, which
   * addressed no store and failed every background resolution (#59432).
   */
  private static final String STORE_ID = SecretStoreRegistry.DEFAULT_STORE_ID;

  @Mock private SecretStore secretStore;
  @Mock private SecretCache secretCache;
  @Mock private ScheduledTaskState scheduledTaskState;
  @Mock private SecretReferenceState secretReferenceState;
  @Mock private ReadonlyStreamProcessorContext context;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private TaskResultBuilder resultBuilder;
  @Mock private StreamClock clock;

  private SecretResolutionScheduler scheduler;

  @BeforeEach
  void setUp() {
    when(scheduledTaskState.getSecretReferenceState()).thenReturn(secretReferenceState);
    when(context.getScheduleService()).thenReturn(scheduleService);
    when(context.getClock()).thenReturn(clock);
    lenient().when(clock.millis()).thenReturn(0L);
    lenient().when(resultBuilder.appendCommandRecord(any(), any())).thenReturn(true);

    final Supplier<ScheduledTaskState> stateFactory = () -> scheduledTaskState;
    final var registry =
        new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, secretCache));
    final var config = new EngineConfiguration();
    scheduler = new SecretResolutionScheduler(stateFactory, registry, config);
    scheduler.onRecovered(context);
  }

  @Test
  void shouldPopulateCacheAndWriteResolutionCompleteOnSuccess() throws Exception {
    // given
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(Set.of("db-password")))
        .thenReturn(Map.of("db-password", new SecretResolutionResult.Resolved("s3cr3t")));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — cache populated before command written
    verify(secretCache).put("db-password", "s3cr3t");
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_COMPLETE);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo(STORE_ID);
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
    assertThat(recordCaptor.getValue().getResolutionState()).isEqualTo(ResolutionState.SUCCESS);
  }

  @Test
  void shouldNotPopulateCacheAndWriteResolutionFailOnPermanentPerSecretError() throws Exception {
    // given
    stubPending(STORE_ID, "missing-key");
    when(secretStore.resolve(Set.of("missing-key")))
        .thenReturn(
            Map.of(
                "missing-key",
                new SecretResolutionResult.Failed(
                    SecretErrorCode.NOT_FOUND, "secret not found", null)));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — cache NOT populated on failure
    verify(secretCache, never()).put(any(), any());
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_FAIL);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo(STORE_ID);
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("missing-key");
    assertThat(recordCaptor.getValue().getResolutionState()).isEqualTo(ResolutionState.NOT_FOUND);
  }

  @Test
  void shouldRetryAndNotWriteCommandOnFirstStoreUnavailable() throws Exception {
    // given
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — first failure: retry, no command written, no cache write
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretCache, never()).put(any(), any());
  }

  @Test
  void shouldWriteResolutionFailAfterMaxRetries() throws Exception {
    // given — drive the scheduler past max retries; a monotonic clock (1 day per call) always
    // exceeds the retry cooldown deadline regardless of how many times it's invoked per cycle
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    final var nowMs = new AtomicLong(0);
    when(clock.millis()).thenAnswer(inv -> nowMs.getAndAdd(Duration.ofDays(1).toMillis()));
    final int maxAttempts = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_MAX_ATTEMPTS;
    for (int i = 0; i < maxAttempts - 1; i++) {
      stubPending(STORE_ID, "db-password");
      scheduler.resolveSecrets(resultBuilder);
    }

    // when — this call hits maxAttempts
    stubPending(STORE_ID, "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_FAIL);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo(STORE_ID);
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
    assertThat(recordCaptor.getValue().getResolutionState())
        .isEqualTo(ResolutionState.STORE_UNAVAILABLE);
  }

  @Test
  void shouldCapBackoffAtRetryMaxDelay() {
    // given
    final var config =
        new EngineConfiguration()
            .setSecretResolutionRetryInitialDelay(Duration.ofSeconds(1))
            .setSecretResolutionRetryBackoffFactor(10)
            .setSecretResolutionRetryMaxDelay(Duration.ofSeconds(2));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, new SecretStoreRegistry(Map.of()), config);

    // when/then
    assertThat(localScheduler.calculateBackoff(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(localScheduler.calculateBackoff(2)).isEqualTo(Duration.ofSeconds(2));
    assertThat(localScheduler.calculateBackoff(5)).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void shouldFailAllRefsWhenRetriesExhausted() throws Exception {
    // given
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("down"));
    final var nowMs = new AtomicLong(0);
    when(clock.millis()).thenAnswer(inv -> nowMs.getAndAdd(Duration.ofDays(1).toMillis()));
    final int maxAttempts = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_MAX_ATTEMPTS;

    // when
    for (int i = 0; i < maxAttempts; i++) {
      stubPending(STORE_ID, "a", "b");
      scheduler.resolveSecrets(resultBuilder);
    }

    // then
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder, times(2))
        .appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_FAIL), recordCaptor.capture());
    assertThat(recordCaptor.getAllValues())
        .extracting(SecretReferenceRecord::getSecretReference)
        .containsExactlyInAnyOrder("a", "b");
    assertThat(recordCaptor.getAllValues())
        .extracting(SecretReferenceRecord::getResolutionState)
        .containsOnly(ResolutionState.STORE_UNAVAILABLE);
  }

  @Test
  void shouldCacheTheValueOfAStoreTheRegistryWasGivenNoCacheFor() throws Exception {
    // given a registry built without a cache for the configured store
    final var registry = new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of());
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(Set.of("db-password")))
        .thenReturn(Map.of("db-password", new SecretResolutionResult.Resolved("v")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then the store's own cache took the value and the reference completes: a configured store
    // always caches, so resolving cannot strand a job by completing it uncached
    assertThat(registry.getStores().get(STORE_ID).lookupLocal("db-password")).contains("v");
    verify(resultBuilder).appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_COMPLETE), any());
  }

  @Test
  void shouldFailAReferenceTheStoreHoldsCachedButNoLongerHas() throws Exception {
    // given a reference the store still holds cached — another partition's scheduler or the resolve
    // API may have read it since the resolution was requested — but that the store has since lost
    final var cache = new InMemorySecretCache();
    cache.put("db-password", "already-held");
    final var registry =
        new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, cache));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(Set.of("db-password")))
        .thenReturn(
            Map.of(
                "db-password",
                new SecretResolutionResult.Failed(SecretErrorCode.NOT_FOUND, "gone", null)));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then the store is asked even for a cached reference and the reference fails: the completion
    // record is durable and exported, so it must not claim a secret the store no longer has
    verify(secretStore).resolve(Set.of("db-password"));
    assertThat(registry.getStores().get(STORE_ID).lookupLocal("db-password")).isEmpty();
    verify(resultBuilder).appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_FAIL), any());
    verify(resultBuilder, never())
        .appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_COMPLETE), any());
  }

  @Test
  void shouldInvalidateOnlyFailedCachedReferencesInAMixedBatch() throws Exception {
    // given two cached references, where an authoritative store read refreshes one and fails the
    // other in the same scheduler batch
    final var cache = new InMemorySecretCache();
    cache.put("db-password", "old-password");
    cache.put("api-key", "old-api-key");
    final var registry =
        new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, cache));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);
    stubPending(STORE_ID, "db-password", "api-key");
    when(secretStore.resolve(Set.of("db-password", "api-key")))
        .thenReturn(
            Map.of(
                "db-password",
                new SecretResolutionResult.Resolved("new-password"),
                "api-key",
                new SecretResolutionResult.Failed(
                    SecretErrorCode.ACCESS_DENIED, "access denied", null)));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then the successful reference is still held locally with the refreshed value, while the
    // failed reference is cleared so later cache-only activation cannot reveal it
    assertThat(registry.getStores().get(STORE_ID).lookupLocal("db-password"))
        .contains("new-password");
    assertThat(registry.getStores().get(STORE_ID).lookupLocal("api-key")).isEmpty();
    verify(resultBuilder).appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_COMPLETE), any());
    verify(resultBuilder).appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_FAIL), any());
  }

  @Test
  void shouldSkipStoreInCooldownPeriod() throws Exception {
    // given — first execute causes a retry (cooldown starts at t=0)
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L);
    scheduler.resolveSecrets(resultBuilder);

    // when — second execute while still in cooldown (clock still at 0)
    stubPending(STORE_ID, "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — store called only once; the cooldown store's ref is excluded during collection, so
    // resolveStore is never invoked for it in cycle 2; no command or cache write
    verify(secretStore, times(1)).resolve(any());
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretCache, never()).put(any(), any());
  }

  @Test
  void shouldResetRetryStateAfterSuccessfulResolution() throws Exception {
    // given — first execute fails (enters retry state)
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any()))
        .thenThrow(new SecretStoreUnavailableException("store down"))
        .thenReturn(Map.of("db-password", new SecretResolutionResult.Resolved("value")));
    when(clock.millis()).thenReturn(Long.MAX_VALUE); // skip cooldown
    scheduler.resolveSecrets(resultBuilder); // first: fails

    // when — second execute succeeds
    stubPending(STORE_ID, "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — cache written, RESOLUTION_COMPLETE written
    verify(secretCache).put("db-password", "value");
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_COMPLETE);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo(STORE_ID);
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
  }

  @Test
  void shouldScheduleEarlierThanIntervalWhenRetryDeadlineIsSooner() throws Exception {
    // given — store fails; retry due in retryInitialDelay (1s) which is < schedulingInterval (5s)
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L);

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — the schedule after the failure uses retryInitialDelay, not schedulingInterval
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    // two calls total: one from onRecovered in setUp, one from execute
    verify(scheduleService, times(2)).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getAllValues().get(1))
        .isEqualTo(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_INITIAL_DELAY)
        .isLessThan(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_INTERVAL);
  }

  @Test
  void shouldPruneRetryStateForStoresWithNoPendingRefs() throws Exception {
    // given — store fails (enters retry state)
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L);
    scheduler.resolveSecrets(resultBuilder);

    // when — store no longer has pending refs
    doReturn(null).when(secretReferenceState).visitPendingSecretReferences(any(), any());
    when(clock.millis()).thenReturn(Long.MAX_VALUE);
    scheduler.resolveSecrets(resultBuilder);

    // then — store not retried; full scheduling interval restored
    verify(secretStore, times(1)).resolve(any());
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    // three calls: onRecovered, first execute (failure → early), second execute (no pending → full)
    verify(scheduleService, times(3)).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getAllValues().get(2))
        .isEqualTo(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_INTERVAL);
  }

  @Test
  void shouldNotResetRetryStateForStoreExcludedByCapAcrossCycle() throws Exception {
    // given — batch limit of 2; store-a and store-b both fail and enter retry cooldown in the
    // same (uncapped) cycle
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var registry =
        new SecretStoreRegistry(Map.of("store-a", storeA, "store-b", storeB), Map.of());
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(2);
    final var localScheduler =
        new SecretResolutionScheduler(() -> scheduledTaskState, registry, config);
    localScheduler.onRecovered(context);

    when(storeA.resolve(Set.of("ref-a1"))).thenThrow(new SecretStoreUnavailableException("down"));
    when(storeB.resolve(Set.of("ref-b1"))).thenThrow(new SecretStoreUnavailableException("down"));
    // cycle 1 (t=0): both stores' single pending ref fit exactly within the cap — nothing capped
    // — so both fail and enter cooldown (attempts=1, nextAttemptAt=1000)
    final var cycle1Refs = new LinkedHashMap<String, String>();
    cycle1Refs.put("store-a", "ref-a1");
    cycle1Refs.put("store-b", "ref-b1");
    stubPending(cycle1Refs);
    when(clock.millis()).thenReturn(0L, 500L, 500L);
    localScheduler.resolveSecrets(resultBuilder);

    // when — cycle 2 (t=500): both store-a and store-b are still within their cooldown window
    // (cooldown entered at t=0 with a 1000ms backoff, cycle 2 runs at t=500), so cooldown-aware
    // collection skips BOTH stores entirely — pendingByStore ends up empty and
    // cooldownSkippedStores = {store-a, store-b}, not "cap consumed by store-a" as under the old
    // per-entry-cap-only behavior
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              if (!visitor.test("store-a", "ref-a1")) {
                return null;
              }
              if (!visitor.test("store-a", "ref-a2")) {
                return null;
              }
              visitor.test("store-b", "ref-b1");
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
    localScheduler.resolveSecrets(resultBuilder);

    // then — cycle 3 (t=500, still short of store-b's t=1000 cooldown deadline): store-b's ref is
    // offered again, but because cycle 2's read left cooldownSkippedStores non-empty, the
    // retainAll prune did NOT run in cycle 2, so store-b's retry state survived into cycle 3 and
    // it is still correctly treated as cooling down — collection excludes it again and resolveStore
    // is never invoked. Had the prune incorrectly run in cycle 2 (wiping store-b's backoff as if it
    // had no pending refs), store-b would be treated as fresh here and resolved immediately instead
    // of honoring the cooldown. store-b's only legitimate resolve() call across all three cycles is
    // the one from cycle 1 (which put it into cooldown in the first place).
    stubPending("store-b", "ref-b1");
    localScheduler.resolveSecrets(resultBuilder);
    verify(storeB, times(1)).resolve(any());
  }

  @Test
  void shouldDoNothingWhenNoPendingReferences() throws Exception {
    // given — visitPendingSecretReferences does nothing (default mock)

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretStore, never()).resolve(any());
    verify(secretCache, never()).put(any(), any());
  }

  @Test
  void shouldFailAllPendingRefsWhenStoreIsNotConfigured() throws Exception {
    // given — pending refs for a store that has no configured SecretStore
    stubPending("unknown-store", "db-password");

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then
    verify(secretStore, never()).resolve(any());
    verify(secretCache, never()).put(any(), any());
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_FAIL);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("unknown-store");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
    assertThat(recordCaptor.getValue().getResolutionState())
        .isEqualTo(ResolutionState.STORE_UNAVAILABLE);
  }

  @Test
  void shouldContinueWithOtherStoresWhenStoreThrowsUnexpectedException() throws Exception {
    // given
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheB = mock(SecretCache.class);
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeA, "store-b", storeB), Map.of("store-b", cacheB));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);
    reset(scheduleService);

    stubPending(Map.of("store-a", "ref-a", "store-b", "ref-b"));
    when(storeA.resolve(Set.of("ref-a"))).thenThrow(new IllegalStateException("boom"));
    when(storeB.resolve(Set.of("ref-b")))
        .thenReturn(Map.of("ref-b", new SecretResolutionResult.Resolved("value-b")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — store-b was still resolved despite store-a's unexpected exception
    verify(cacheB).put("ref-b", "value-b");
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_COMPLETE);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("store-b");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("ref-b");
    // only this cycle's reschedule remains after the reset above
    verify(scheduleService).runDelayedAsync(any(), any(), any());
  }

  @Test
  void shouldLeaveRefsPendingWhenStoreThrowsUnexpectedException() throws Exception {
    // given
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(any())).thenThrow(new IllegalStateException("boom"));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — the failure did not enter retry/backoff state
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretCache, never()).put(any(), any());
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    // two calls total: one from onRecovered in setUp, one from execute
    verify(scheduleService, times(2)).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getAllValues().get(1))
        .isEqualTo(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_INTERVAL);
  }

  @Test
  void shouldResolveOtherStoreWhenOneStoreIsUnavailable() throws Exception {
    // given
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheB = mock(SecretCache.class);
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeA, "store-b", storeB), Map.of("store-b", cacheB));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);

    stubPending(Map.of("store-a", "ref-a", "store-b", "ref-b"));
    when(storeA.resolve(Set.of("ref-a"))).thenThrow(new SecretStoreUnavailableException("down"));
    when(storeB.resolve(Set.of("ref-b")))
        .thenReturn(Map.of("ref-b", new SecretResolutionResult.Resolved("value-b")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then
    verify(cacheB).put("ref-b", "value-b");
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_COMPLETE);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("store-b");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("ref-b");
  }

  @Test
  void shouldBatchAllRefsOfStoreIntoSingleResolveCall() throws Exception {
    // given
    stubPending(STORE_ID, "ref-1", "ref-2");
    when(secretStore.resolve(Set.of("ref-1", "ref-2")))
        .thenReturn(
            Map.of(
                "ref-1", new SecretResolutionResult.Resolved("value-1"),
                "ref-2", new SecretResolutionResult.Resolved("value-2")));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then
    verify(secretStore, times(1)).resolve(Set.of("ref-1", "ref-2"));
    verify(resultBuilder, times(2))
        .appendCommandRecord(eq(SecretReferenceIntent.RESOLUTION_COMPLETE), any());
  }

  @Test
  void shouldCapRefsCollectedForASingleStoreToBatchLimit() throws Exception {
    // given — batch limit of 2, but 3 refs pending in one store
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(2);
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, secretCache)),
            config);
    localScheduler.onRecovered(context);
    stubPending(STORE_ID, "ref-1", "ref-2", "ref-3");
    when(secretStore.resolve(Set.of("ref-1", "ref-2")))
        .thenReturn(
            Map.of(
                "ref-1", new SecretResolutionResult.Resolved("value-1"),
                "ref-2", new SecretResolutionResult.Resolved("value-2")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — only the first 2 refs (the batch limit) were resolved; the 3rd stays pending
    final var refsCaptor = ArgumentCaptor.forClass(Set.class);
    verify(secretStore).resolve(refsCaptor.capture());
    assertThat(refsCaptor.getAllValues()).containsExactly(Set.of("ref-1", "ref-2"));
  }

  @Test
  void shouldCapAcrossMultipleStoresNotPerStore() throws Exception {
    // given — global batch limit of 3; two stores contribute pending refs, so the cap applies
    // across both stores combined, not per store individually
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheA = mock(SecretCache.class);
    final var cacheB = mock(SecretCache.class);
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeA, "store-b", storeB),
            Map.of("store-a", cacheA, "store-b", cacheB));
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(3);
    final var localScheduler =
        new SecretResolutionScheduler(() -> scheduledTaskState, registry, config);
    localScheduler.onRecovered(context);

    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              if (!visitor.test("store-a", "ref-a1")) {
                return null;
              }
              if (!visitor.test("store-a", "ref-a2")) {
                return null;
              }
              if (!visitor.test("store-b", "ref-b1")) {
                return null;
              }
              visitor.test("store-b", "ref-b2");
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
    when(storeA.resolve(Set.of("ref-a1", "ref-a2")))
        .thenReturn(
            Map.of(
                "ref-a1", new SecretResolutionResult.Resolved("v1"),
                "ref-a2", new SecretResolutionResult.Resolved("v2")));
    when(storeB.resolve(Set.of("ref-b1")))
        .thenReturn(Map.of("ref-b1", new SecretResolutionResult.Resolved("v3")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — store-a's 2 refs plus only the first of store-b's 2 refs (3 total, the cap);
    // store-b's second ref ("ref-b2") is left pending for the next cycle
    final var storeARefsCaptor = ArgumentCaptor.forClass(Set.class);
    final var storeBRefsCaptor = ArgumentCaptor.forClass(Set.class);
    verify(storeA).resolve(storeARefsCaptor.capture());
    verify(storeB).resolve(storeBRefsCaptor.capture());
    assertThat(storeARefsCaptor.getAllValues()).containsExactly(Set.of("ref-a1", "ref-a2"));
    assertThat(storeBRefsCaptor.getAllValues()).containsExactly(Set.of("ref-b1"));
  }

  @Test
  void shouldRescheduleImmediatelyWhenPendingRefsExceedBatchLimit() throws Exception {
    // given — batch limit of 2, but 3 refs pending
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(2);
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, secretCache)),
            config);
    localScheduler.onRecovered(context);
    reset(scheduleService);
    stubPending(STORE_ID, "ref-1", "ref-2", "ref-3");
    when(secretStore.resolve(any()))
        .thenReturn(
            Map.of(
                "ref-1", new SecretResolutionResult.Resolved("value-1"),
                "ref-2", new SecretResolutionResult.Resolved("value-2")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — rescheduled with zero delay instead of the normal interval
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(scheduleService).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getValue()).isEqualTo(Duration.ZERO);
  }

  @Test
  void shouldNotRescheduleImmediatelyWhenPendingRefsAreWithinBatchLimit() throws Exception {
    // given — batch limit of 2, exactly 2 refs pending (nothing left over)
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(2);
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, secretCache)),
            config);
    localScheduler.onRecovered(context);
    reset(scheduleService);
    stubPending(STORE_ID, "ref-1", "ref-2");
    when(secretStore.resolve(Set.of("ref-1", "ref-2")))
        .thenReturn(
            Map.of(
                "ref-1", new SecretResolutionResult.Resolved("value-1"),
                "ref-2", new SecretResolutionResult.Resolved("value-2")));

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — the normal scheduling interval is used, since nothing was capped
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(scheduleService).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getValue())
        .isEqualTo(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_INTERVAL);
  }

  @Test
  void shouldNotRescheduleImmediatelyWhenCappedStoreIsInRetryCooldown() throws Exception {
    // given — batch limit of 1, but 2 refs pending for a store that is also in retry cooldown;
    // the cooldown-aware collection must exclude the cooldown store's refs entirely rather than
    // let them consume the cap, otherwise the scheduler could busy-spin re-scanning state at zero
    // delay until the cooldown expires by wall clock
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(1);
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            new SecretStoreRegistry(Map.of(STORE_ID, secretStore), Map.of(STORE_ID, secretCache)),
            config);
    localScheduler.onRecovered(context);
    reset(scheduleService);
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));

    // when — first cycle: store fails, enters retry cooldown
    stubPending(STORE_ID, "ref-1", "ref-2");
    localScheduler.resolveSecrets(resultBuilder);

    // when — second cycle: still within the cooldown window; the cooldown store's ref is excluded
    // from collection entirely, so nothing is capped and nothing is resolved
    stubPending(STORE_ID, "ref-1", "ref-2");
    localScheduler.resolveSecrets(resultBuilder);

    // then — the store was only attempted once; the second cycle skipped it during collection
    verify(secretStore, times(1)).resolve(any());

    // then — the reschedule after the second cycle uses the cooldown-aware delay, not Duration.ZERO
    // (nothing was capped this cycle, so it falls through to computeNextDelay)
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(scheduleService, times(2)).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getAllValues().get(1))
        .isEqualTo(EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_INITIAL_DELAY)
        .isNotEqualTo(Duration.ZERO);
  }

  @Test
  void shouldResumeCollectionFromCursorOnNextCycle() throws Exception {
    // given — cycle 1's collection stops early and reports a resume cursor at the last entry the
    // visitor was offered before being told to stop
    final var cycle1Cursor = new SecretReferenceState.PendingRefCursor(STORE_ID, "ref-1");
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              visitor.test(STORE_ID, "ref-1");
              return cycle1Cursor;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());

    // when — cycle 1
    scheduler.resolveSecrets(resultBuilder);

    // and — cycle 2
    stubPending(STORE_ID, "ref-2");
    scheduler.resolveSecrets(resultBuilder);

    // then — cycle 2 resumed from exactly where cycle 1 left off, not from the beginning; this is
    // the fairness fix that lets later stores (in key order) eventually get served instead of a
    // fresh scan always restarting at the start of the column family
    final var cursorCaptor = ArgumentCaptor.forClass(SecretReferenceState.PendingRefCursor.class);
    verify(secretReferenceState, times(2))
        .visitPendingSecretReferences(cursorCaptor.capture(), any());
    assertThat(cursorCaptor.getAllValues().get(0)).isNull();
    assertThat(cursorCaptor.getAllValues().get(1)).isEqualTo(cycle1Cursor);
  }

  @Test
  void shouldNotLetCooldownStoreConsumeCapBudgetFromHealthyStore() throws Exception {
    // given — batch limit of 2; store-a is already in retry cooldown with 3 pending refs, store-b
    // is healthy with 2 pending refs. Under the old (collection-time-cooldown-unaware) behavior,
    // the batch limit would have been entirely consumed by store-a's refs, starving store-b.
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheB = mock(SecretCache.class);
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeA, "store-b", storeB), Map.of("store-b", cacheB));
    final var config = new EngineConfiguration().setSecretResolutionBatchLimit(2);
    final var localScheduler =
        new SecretResolutionScheduler(() -> scheduledTaskState, registry, config);
    localScheduler.onRecovered(context);

    // cycle 1 (t=0): drive store-a into retry cooldown
    when(storeA.resolve(Set.of("ref-a1"))).thenThrow(new SecretStoreUnavailableException("down"));
    stubPending("store-a", "ref-a1");
    localScheduler.resolveSecrets(resultBuilder);

    // when — cycle 2 (t=0, still within store-a's cooldown window): store-a now has 3 pending
    // refs (offered first, in key order) and store-b has 2 pending refs
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              if (!visitor.test("store-a", "ref-a1")) {
                return new SecretReferenceState.PendingRefCursor("store-a", "ref-a1");
              }
              if (!visitor.test("store-a", "ref-a2")) {
                return new SecretReferenceState.PendingRefCursor("store-a", "ref-a2");
              }
              if (!visitor.test("store-a", "ref-a3")) {
                return new SecretReferenceState.PendingRefCursor("store-a", "ref-a3");
              }
              if (!visitor.test("store-b", "ref-b1")) {
                return new SecretReferenceState.PendingRefCursor("store-b", "ref-b1");
              }
              if (!visitor.test("store-b", "ref-b2")) {
                return new SecretReferenceState.PendingRefCursor("store-b", "ref-b2");
              }
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
    when(storeB.resolve(Set.of("ref-b1", "ref-b2")))
        .thenReturn(
            Map.of(
                "ref-b1", new SecretResolutionResult.Resolved("v1"),
                "ref-b2", new SecretResolutionResult.Resolved("v2")));
    localScheduler.resolveSecrets(resultBuilder);

    // then — store-b's refs were not starved by store-a's cooldown refs consuming the cap
    final var storeBRefsCaptor = ArgumentCaptor.forClass(Set.class);
    verify(storeB).resolve(storeBRefsCaptor.capture());
    assertThat(storeBRefsCaptor.getValue()).isEqualTo(Set.of("ref-b1", "ref-b2"));
    // store-a was never retried while cooling down — its only resolve() call is from cycle 1
    verify(storeA, times(1)).resolve(any());
  }

  @Test
  void shouldStopProcessingFurtherStoresWhenTaskResultBatchIsFull() throws Exception {
    // given — appendCommandRecord reports the task result batch as full
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheA = mock(SecretCache.class);
    final var registry =
        new SecretStoreRegistry(
            Map.of("store-a", storeA, "store-b", storeB), Map.of("store-a", cacheA));
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState, registry, new EngineConfiguration());
    localScheduler.onRecovered(context);

    final var refsByStore = new LinkedHashMap<String, String>();
    refsByStore.put("store-a", "ref-a");
    refsByStore.put("store-b", "ref-b");
    stubPending(refsByStore);
    when(storeA.resolve(Set.of("ref-a")))
        .thenReturn(Map.of("ref-a", new SecretResolutionResult.Resolved("value-a")));
    when(resultBuilder.appendCommandRecord(any(), any())).thenReturn(false);

    // when
    localScheduler.resolveSecrets(resultBuilder);

    // then — store-a was attempted (its append hit the full batch), store-b never reached
    verify(storeA).resolve(Set.of("ref-a"));
    verify(storeB, never()).resolve(any());
  }

  @Test
  void shouldRescheduleImmediatelyWhenTaskResultBatchIsFull() throws Exception {
    // given
    stubPending(STORE_ID, "db-password");
    when(secretStore.resolve(Set.of("db-password")))
        .thenReturn(Map.of("db-password", new SecretResolutionResult.Resolved("s3cr3t")));
    when(resultBuilder.appendCommandRecord(any(), any())).thenReturn(false);

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — rescheduled with zero delay instead of the normal interval
    final var delayCaptor = ArgumentCaptor.forClass(Duration.class);
    // two calls: onRecovered's initial schedule (in setUp), then this cycle's reschedule
    verify(scheduleService, times(2)).runDelayedAsync(delayCaptor.capture(), any(), any());
    assertThat(delayCaptor.getAllValues().get(1)).isEqualTo(Duration.ZERO);
  }

  @Test
  void shouldNotScheduleSecondChainWhenResumedWhileTaskStillPending() throws Exception {
    // given — setUp already scheduled once via onRecovered

    // when
    scheduler.onPaused();
    scheduler.onResumed();

    // then — the parked task from onRecovered still owns the chain
    verify(scheduleService, times(1)).runDelayedAsync(any(), any(), any());
  }

  @Test
  void shouldNotRescheduleWhilePausedAndScheduleOnceOnResume() throws Exception {
    // given
    scheduler.onPaused();

    // when — executing while paused must not reschedule
    scheduler.resolveSecrets(resultBuilder);

    // then
    verify(scheduleService, times(1)).runDelayedAsync(any(), any(), any());

    // when — resuming starts exactly one new chain
    scheduler.onResumed();

    // then
    verify(scheduleService, times(2)).runDelayedAsync(any(), any(), any());
  }

  @Test
  void shouldRetryStoreAfterCooldownElapses() throws Exception {
    // given
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L, 10_000L);

    stubPending(STORE_ID, "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // when — cooldown (1s) has elapsed by the time of the second run (clock now at 10s)
    stubPending(STORE_ID, "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — second attempt happened because the cooldown expired
    verify(secretStore, times(2)).resolve(any());
  }

  private void stubPending(final String storeId, final String secretRef) {
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              if (!visitor.test(storeId, secretRef)) {
                return new SecretReferenceState.PendingRefCursor(storeId, secretRef);
              }
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
  }

  private void stubPending(final String storeId, final String... secretRefs) {
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              for (final String secretRef : secretRefs) {
                if (!visitor.test(storeId, secretRef)) {
                  return new SecretReferenceState.PendingRefCursor(storeId, secretRef);
                }
              }
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
  }

  private void stubPending(final Map<String, String> refsByStore) {
    doAnswer(
            inv -> {
              final var visitor = (BiPredicate<String, String>) inv.getArgument(1);
              for (final var entry : refsByStore.entrySet()) {
                if (!visitor.test(entry.getKey(), entry.getValue())) {
                  return new SecretReferenceState.PendingRefCursor(
                      entry.getKey(), entry.getValue());
                }
              }
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any(), any());
  }
}
