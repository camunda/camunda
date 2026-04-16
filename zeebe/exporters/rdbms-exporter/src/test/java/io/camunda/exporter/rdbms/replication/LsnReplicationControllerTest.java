/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.NoopReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.ReplicationStatusDto;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LsnReplicationControllerTest {

  @Test
  void shouldScheduleTaskOnCreationWithConfiguredDuration() {
    // given
    final var fixture = new TestFixture();

    // when
    // created in fixture

    // then
    verify(fixture.controller).scheduleCancellableTask(eq(TestFixture.POLLING_INTERVAL), any());
    assertThat(fixture.scheduledChecks).hasSize(1);
  }

  @Test
  void shouldCancelTaskOnClose() throws Exception {
    // given
    final var fixture = new TestFixture();

    // when
    fixture.replicationController.close();

    // then
    verify(fixture.initialScheduledTask).cancel();
  }

  @Test
  void shouldAddPendingEntryOnFlush() {
    // given
    final var fixture = new TestFixture();
    when(fixture.lsnProvider.getCurrent()).thenReturn(100L);

    // when
    fixture.replicationController.onFlush(42L);

    // then
    assertThat(fixture.replicationController.pendingEntriesSize()).isEqualTo(1);
  }

  @Test
  void shouldAlwaysRescheduleTaskOnCheck() {
    // given
    final var fixture = new TestFixture();
    when(fixture.lsnProvider.getReplicationStatuses()).thenThrow(new RuntimeException("boom"));

    // when
    fixture.firstScheduledCheck().run();

    // then
    verify(fixture.controller, times(2))
        .scheduleCancellableTask(eq(TestFixture.POLLING_INTERVAL), any());
    assertThat(fixture.scheduledChecks).hasSize(2);
  }

  @Test
  void shouldUpdatePositionWhenNewConfirmedPositionIsHigher() {
    // given
    final var fixture = new TestFixture();
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 10L)));
    fixture.replicationController.onFlush(42L);

    // when
    fixture.firstScheduledCheck().run();

    // then
    verify(fixture.controller).updateLastExportedRecordPosition(42L);

    // when 2
    when(fixture.lsnProvider.getCurrent()).thenReturn(20L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 20L)));
    fixture.replicationController.onFlush(52L);

    fixture.lastScheduledCheck().run();

    verify(fixture.controller).updateLastExportedRecordPosition(52L);
  }

  @Test
  void shouldNotUpdatePositionWhenNewConfirmedPositionIsNotHigher() {
    // given
    final var fixture = new TestFixture();
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L, 20L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 10L)))
        .thenReturn(List.of(replicationStatus("replica-1", 20L)));

    fixture.replicationController.onFlush(42L);
    fixture.firstScheduledCheck().run();

    fixture.replicationController.onFlush(42L);

    // when
    fixture.lastScheduledCheck().run();

    // then
    verify(fixture.controller, times(1)).updateLastExportedRecordPosition(42L);
    verify(fixture.controller, never()).updateLastExportedRecordPosition(20L);
  }

  @Test
  void shouldWaitForRequiredSyncReplicasBeforeConfirmingPosition() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(2);
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    fixture.replicationController.onFlush(42L);

    // when: only one replica caught up
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 10L)));
    fixture.firstScheduledCheck().run();

    // then
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);

    // when: second replica also caught up
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(
            List.of(replicationStatus("replica-1", 10L), replicationStatus("replica-2", 10L)));
    fixture.lastScheduledCheck().run();

    // then
    verify(fixture.controller).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldComputeConfirmedLsnFromSecondHighestReplicaWhenThreeReplicasPresent() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(2);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(
            List.of(
                replicationStatus("replica-1", 120L),
                replicationStatus("replica-2", 80L),
                replicationStatus("replica-3", 50L)));

    // when
    final long confirmedLsn = fixture.replicationController.computeConfirmedLsn();

    // then
    assertThat(confirmedLsn).isEqualTo(80L);
  }

  @Test
  void shouldComputeConfirmedLsnFromLowerReplicaWhenTwoReplicasPresent() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(2);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(
            List.of(replicationStatus("replica-1", 120L), replicationStatus("replica-2", 80L)));

    // when
    final long confirmedLsn = fixture.replicationController.computeConfirmedLsn();

    // then
    assertThat(confirmedLsn).isEqualTo(80L);
  }

  @Test
  void shouldReturnMinusOneWhenNotEnoughReplicasPresentForMinSyncReplicas() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(2);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 120L)));

    // when
    final long confirmedLsn = fixture.replicationController.computeConfirmedLsn();

    // then
    assertThat(confirmedLsn).isEqualTo(-1L);
  }

  @Test
  void shouldConfirmPositionWhenMaxLagExceeded() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    // replica never catches up
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: first check — not yet expired
    fixture.firstScheduledCheck().run();

    // then: position not confirmed (replica hasn't caught up, maxLag not exceeded)
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);

    // when: advance clock past maxLag
    fixture.clock.advance(Duration.ofMinutes(6));
    fixture.lastScheduledCheck().run();

    // then: position confirmed due to maxLag
    verify(fixture.controller).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldNotConfirmPositionWhenMaxLagNotExceeded() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    // replica never catches up
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: check within maxLag
    fixture.clock.advance(Duration.ofMinutes(3));
    fixture.firstScheduledCheck().run();

    // then: position not confirmed
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldConfirmPositionWhenDbReportedLagExceedsMaxLagAndPauseDisabled() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    fixture.replicationConfiguration.setPauseOnMaxLagExceeded(false);
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    // replica never catches up via LSN
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: DB reports a lag larger than maxLag
    when(fixture.lsnProvider.getReplicationLag()).thenReturn(Duration.ofMinutes(6));
    fixture.firstScheduledCheck().run();

    // then: position confirmed due to DB-reported lag exceeding maxLag
    verify(fixture.controller).updateLastExportedRecordPosition(42L);
    assertThat(fixture.replicationController.isPaused()).isFalse();
  }

  @Test
  void shouldPauseWhenDbReportedLagExceedsMaxLagAndPauseEnabled() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    // pauseOnMaxLagExceeded is true by default
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: DB reports a lag larger than maxLag
    when(fixture.lsnProvider.getReplicationLag()).thenReturn(Duration.ofMinutes(6));
    fixture.firstScheduledCheck().run();

    // then: position NOT confirmed — we pause instead of force-confirming
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);
    assertThat(fixture.replicationController.isPaused()).isTrue();
  }

  @Test
  void shouldResumeWhenDbReportedLagDropsBelowMaxLag() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: DB reports a lag larger than maxLag — controller pauses
    when(fixture.lsnProvider.getReplicationLag()).thenReturn(Duration.ofMinutes(6));
    fixture.firstScheduledCheck().run();
    assertThat(fixture.replicationController.isPaused()).isTrue();

    // when: DB lag drops below maxLag
    when(fixture.lsnProvider.getReplicationLag()).thenReturn(Duration.ofMinutes(2));
    fixture.lastScheduledCheck().run();

    // then: controller unpauses
    assertThat(fixture.replicationController.isPaused()).isFalse();
  }

  @Test
  void shouldNotConfirmPositionWhenDbReportedLagBelowMaxLag() {
    // given
    final var fixture = new TestFixture();
    fixture.replicationConfiguration.setMinSyncReplicas(1);
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    when(fixture.lsnProvider.getCurrent()).thenReturn(10L);
    when(fixture.lsnProvider.getReplicationStatuses())
        .thenReturn(List.of(replicationStatus("replica-1", 5L)));
    fixture.replicationController.onFlush(42L);

    // when: DB reports a lag smaller than maxLag
    when(fixture.lsnProvider.getReplicationLag()).thenReturn(Duration.ofMinutes(3));
    fixture.firstScheduledCheck().run();

    // then: position not confirmed
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldConfirmPositionViaMaxLagWhenUsingNoopProvider() {
    // given
    final var fixture = new TestFixture(new NoopReplicationLogStatusProvider());
    fixture.replicationConfiguration.setMaxLag(Duration.ofMinutes(5));
    fixture.replicationController.onFlush(42L);

    // when: first check — not yet expired
    fixture.firstScheduledCheck().run();

    // then: not yet confirmed
    verify(fixture.controller, never()).updateLastExportedRecordPosition(42L);

    // when: advance clock past maxLag
    fixture.clock.advance(Duration.ofMinutes(6));
    fixture.lastScheduledCheck().run();

    // then: position confirmed via maxLag
    verify(fixture.controller).updateLastExportedRecordPosition(42L);
  }

  @Test
  void shouldReturnMinusOneFromComputeConfirmedLsnWhenUsingNoopProvider() {
    // given
    final var fixture = new TestFixture(new NoopReplicationLogStatusProvider());

    // when
    final long confirmedLsn = fixture.replicationController.computeConfirmedLsn();

    // then: noop provider returns -1 for getCurrent(), so computeConfirmedLsn() returns MIN_VALUE
    assertThat(confirmedLsn).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void
      shouldReturnMinusOneFromComputeConfirmedLsnWhenUsingNoopProviderEvenWithZeroMinSyncReplicas() {
    // given
    final var fixture = new TestFixture(new NoopReplicationLogStatusProvider());
    fixture.replicationConfiguration.setMinSyncReplicas(0);

    // when
    final long confirmedLsn = fixture.replicationController.computeConfirmedLsn();

    // then: noop provider signals LSN unavailable, so MIN_VALUE regardless of minSyncReplicas
    assertThat(confirmedLsn).isEqualTo(Long.MIN_VALUE);
  }

  private static ReplicationStatusDto replicationStatus(final String replicaId, final long lsn) {
    final var status = new ReplicationStatusDto();
    status.setReplicaId(replicaId);
    status.setLogStatus(lsn);
    return status;
  }

  private static final class TestFixture {
    private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);

    private final Controller controller = mock(Controller.class);
    private final ReplicationLogStatusProvider lsnProvider;
    private final ScheduledTask initialScheduledTask = mock(ScheduledTask.class);
    private final List<Runnable> scheduledChecks = new ArrayList<>();
    private final ReplicationConfiguration replicationConfiguration =
        new ReplicationConfiguration();
    private final TestClock clock = new TestClock();

    private final LsnReplicationController replicationController;

    private int scheduleInvocations = 0;

    private TestFixture() {
      this(mock(ReplicationLogStatusProvider.class));
    }

    private TestFixture(final ReplicationLogStatusProvider provider) {
      lsnProvider = provider;
      replicationConfiguration.setPollingInterval(POLLING_INTERVAL);

      // Default DB-reported lag to zero so existing tests behave as before.
      if (mockingDetails(lsnProvider).isMock()) {
        when(lsnProvider.getReplicationLag()).thenReturn(Duration.ZERO);
      }

      when(controller.scheduleCancellableTask(eq(POLLING_INTERVAL), any()))
          .thenAnswer(
              invocation -> {
                scheduledChecks.add(invocation.getArgument(1, Runnable.class));
                if (scheduleInvocations++ == 0) {
                  return initialScheduledTask;
                }
                return mock(ScheduledTask.class);
              });

      replicationController =
          new LsnReplicationController(controller, lsnProvider, replicationConfiguration, 1, clock);
    }

    private Runnable firstScheduledCheck() {
      return scheduledChecks.getFirst();
    }

    private Runnable lastScheduledCheck() {
      return scheduledChecks.getLast();
    }
  }

  private static final class TestClock implements InstantSource {
    private final AtomicLong currentTimeMs = new AtomicLong(System.currentTimeMillis());

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(currentTimeMs.get());
    }

    @Override
    public long millis() {
      return currentTimeMs.get();
    }

    void advance(final Duration duration) {
      currentTimeMs.addAndGet(duration.toMillis());
    }
  }
}
