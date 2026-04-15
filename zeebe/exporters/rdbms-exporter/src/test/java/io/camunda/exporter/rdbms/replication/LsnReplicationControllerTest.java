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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.ReplicationLogStatusProvider;
import io.camunda.db.rdbms.write.ReplicationStatusDto;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

  private static ReplicationStatusDto replicationStatus(final String replicaId, final long lsn) {
    final var status = new ReplicationStatusDto();
    status.setReplicaId(replicaId);
    status.setLogStatus(lsn);
    return status;
  }

  private static final class TestFixture {
    private static final Duration POLLING_INTERVAL = Duration.ofSeconds(5);

    private final Controller controller = mock(Controller.class);
    private final ReplicationLogStatusProvider lsnProvider =
        mock(ReplicationLogStatusProvider.class);
    private final ScheduledTask initialScheduledTask = mock(ScheduledTask.class);
    private final List<Runnable> scheduledChecks = new ArrayList<>();
    private final ReplicationConfiguration replicationConfiguration =
        new ReplicationConfiguration();

    private final LsnReplicationController replicationController;

    private int scheduleInvocations = 0;

    private TestFixture() {
      replicationConfiguration.setPollingInterval(POLLING_INTERVAL);

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
          new LsnReplicationController(controller, lsnProvider, replicationConfiguration, 1);
    }

    private Runnable firstScheduledCheck() {
      return scheduledChecks.getFirst();
    }

    private Runnable lastScheduledCheck() {
      return scheduledChecks.getLast();
    }
  }
}
