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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class SecretResolutionSchedulerTest {

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

    final Supplier<ScheduledTaskState> stateFactory = () -> scheduledTaskState;
    final Map<String, SecretStore> stores = Map.of("my-store", secretStore);
    final Map<String, SecretCache> caches = Map.of("my-store", secretCache);
    final var config = new EngineConfiguration();
    scheduler = new SecretResolutionScheduler(stateFactory, stores, caches, config);
    scheduler.onRecovered(context);
  }

  @Test
  void shouldPopulateCacheAndWriteResolutionCompleteOnSuccess() throws Exception {
    // given
    stubPending("my-store", "db-password");
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
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("my-store");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
  }

  @Test
  void shouldNotPopulateCacheAndWriteResolutionFailOnPermanentPerSecretError() throws Exception {
    // given
    stubPending("my-store", "missing-key");
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
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("my-store");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("missing-key");
  }

  @Test
  void shouldRetryAndNotWriteCommandOnFirstStoreUnavailable() throws Exception {
    // given
    stubPending("my-store", "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));

    // when
    scheduler.resolveSecrets(resultBuilder);

    // then — first failure: retry, no command written, no cache write
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretCache, never()).put(any(), any());
  }

  @Test
  void shouldWriteResolutionFailAfterMaxRetries() throws Exception {
    // given — drive the scheduler past max retries; each value must exceed the previous cooldown
    // deadline (backoff after failure 1 is 1s, after failure 2 is 2s)
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L, 10_000L, 100_000L);
    final int maxAttempts = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_MAX_ATTEMPTS;
    for (int i = 0; i < maxAttempts - 1; i++) {
      stubPending("my-store", "db-password");
      scheduler.resolveSecrets(resultBuilder);
    }

    // when — this call hits maxAttempts
    stubPending("my-store", "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_FAIL);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("my-store");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
  }

  @Test
  void shouldSkipStoreInCooldownPeriod() throws Exception {
    // given — first execute causes a retry (cooldown starts at t=0)
    stubPending("my-store", "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L);
    scheduler.resolveSecrets(resultBuilder);

    // when — second execute while still in cooldown (clock still at 0)
    stubPending("my-store", "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — store called only once; no command or cache write
    verify(secretStore, times(1)).resolve(any());
    verify(resultBuilder, never()).appendCommandRecord(any(), any());
    verify(secretCache, never()).put(any(), any());
  }

  @Test
  void shouldResetRetryStateAfterSuccessfulResolution() throws Exception {
    // given — first execute fails (enters retry state)
    stubPending("my-store", "db-password");
    when(secretStore.resolve(any()))
        .thenThrow(new SecretStoreUnavailableException("store down"))
        .thenReturn(Map.of("db-password", new SecretResolutionResult.Resolved("value")));
    when(clock.millis()).thenReturn(Long.MAX_VALUE); // skip cooldown
    scheduler.resolveSecrets(resultBuilder); // first: fails

    // when — second execute succeeds
    stubPending("my-store", "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — cache written, RESOLUTION_COMPLETE written
    verify(secretCache).put("db-password", "value");
    final var intentCaptor = ArgumentCaptor.forClass(SecretReferenceIntent.class);
    final var recordCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(resultBuilder).appendCommandRecord(intentCaptor.capture(), recordCaptor.capture());
    assertThat(intentCaptor.getValue()).isEqualTo(SecretReferenceIntent.RESOLUTION_COMPLETE);
    assertThat(recordCaptor.getValue().getStoreId()).isEqualTo("my-store");
    assertThat(recordCaptor.getValue().getSecretReference()).isEqualTo("db-password");
  }

  @Test
  void shouldScheduleEarlierThanIntervalWhenRetryDeadlineIsSooner() throws Exception {
    // given — store fails; retry due in retryInitialDelay (1s) which is < schedulingInterval (5s)
    stubPending("my-store", "db-password");
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
    stubPending("my-store", "db-password");
    when(secretStore.resolve(any())).thenThrow(new SecretStoreUnavailableException("store down"));
    when(clock.millis()).thenReturn(0L);
    scheduler.resolveSecrets(resultBuilder);

    // when — store no longer has pending refs
    doNothing().when(secretReferenceState).visitPendingSecretReferences(any());
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
  }

  @Test
  void shouldContinueWithOtherStoresWhenStoreThrowsUnexpectedException() throws Exception {
    // given
    final var storeA = mock(SecretStore.class);
    final var storeB = mock(SecretStore.class);
    final var cacheB = mock(SecretCache.class);
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            Map.of("store-a", storeA, "store-b", storeB),
            Map.of("store-b", cacheB),
            new EngineConfiguration());
    localScheduler.onRecovered(context);

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
    // scheduler.onRecovered (setUp) + localScheduler.onRecovered + localScheduler's own reschedule
    verify(scheduleService, times(3)).runDelayedAsync(any(), any(), any());
  }

  @Test
  void shouldLeaveRefsPendingWhenStoreThrowsUnexpectedException() throws Exception {
    // given
    stubPending("my-store", "db-password");
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
    final var localScheduler =
        new SecretResolutionScheduler(
            () -> scheduledTaskState,
            Map.of("store-a", storeA, "store-b", storeB),
            Map.of("store-b", cacheB),
            new EngineConfiguration());
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
    stubPending("my-store", "ref-1", "ref-2");
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

    stubPending("my-store", "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // when — cooldown (1s) has elapsed by the time of the second run (clock now at 10s)
    stubPending("my-store", "db-password");
    scheduler.resolveSecrets(resultBuilder);

    // then — second attempt happened because the cooldown expired
    verify(secretStore, times(2)).resolve(any());
  }

  private void stubPending(final String storeId, final String secretRef) {
    doAnswer(
            inv -> {
              final var visitor = (BiConsumer<String, String>) inv.getArgument(0);
              visitor.accept(storeId, secretRef);
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any());
  }

  private void stubPending(final String storeId, final String... secretRefs) {
    doAnswer(
            inv -> {
              final var visitor = (BiConsumer<String, String>) inv.getArgument(0);
              for (final String secretRef : secretRefs) {
                visitor.accept(storeId, secretRef);
              }
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any());
  }

  private void stubPending(final Map<String, String> refsByStore) {
    doAnswer(
            inv -> {
              final var visitor = (BiConsumer<String, String>) inv.getArgument(0);
              refsByStore.forEach(visitor::accept);
              return null;
            })
        .when(secretReferenceState)
        .visitPendingSecretReferences(any());
  }
}
