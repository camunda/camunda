/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderImpl.LEGACY_TOPIC_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.transport.backupapi.CheckpointSnapshotReserver;
import io.camunda.zeebe.broker.transport.backupapi.SnapshotTrigger;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class InterPartitionCommandCheckpointTest {

  private final ClusterCommunicationService communicationService;
  private final LogStreamWriter logStreamWriter;
  private final InterPartitionCommandSenderImpl sender;
  private final PersistedSnapshotStore snapshotStore;
  private final SnapshotTrigger snapshotTrigger;
  private final InterPartitionCommandReceiverImpl receiver;

  InterPartitionCommandCheckpointTest(
      @Mock final ClusterCommunicationService communicationService,
      @Mock(answer = Answers.RETURNS_SELF) final LogStreamWriter logStreamWriter,
      @Mock final PersistedSnapshotStore snapshotStore,
      @Mock final SnapshotTrigger snapshotTrigger,
      @Mock final CheckpointState checkpointState) {
    this.communicationService = communicationService;
    this.logStreamWriter = logStreamWriter;
    this.snapshotStore = snapshotStore;
    this.snapshotTrigger = snapshotTrigger;

    sender = new InterPartitionCommandSenderImpl(communicationService, LEGACY_TOPIC_PREFIX);
    sender.setCurrentLeader(1, 2);
    final var concurrencyControl = new TestConcurrencyControl();
    receiver =
        new InterPartitionCommandReceiverImpl(
            logStreamWriter,
            new CheckpointSnapshotReserver(
                snapshotStore, snapshotTrigger, checkpointState, concurrencyControl),
            concurrencyControl);
    lenient()
        .when(checkpointState.getLatestCheckpointId())
        .thenReturn(CheckpointState.NO_CHECKPOINT);
    // no fresh snapshot is taken unless a test says so, so the latest one is reserved
    lenient()
        .when(snapshotTrigger.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(null));
    lenient()
        .when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.empty()));
  }

  @Test
  void shouldReserveFreshSnapshotForNewCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var freshSnapshot = mock(PersistedSnapshot.class);
    when(freshSnapshot.getId()).thenReturn("fresh");
    when(snapshotTrigger.forceSnapshot())
        .thenReturn(CompletableActorFuture.completed(freshSnapshot));
    when(snapshotStore.reserveSnapshot(anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    sender.setCheckpointInfo(17, CheckpointType.SCHEDULED_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(snapshotTrigger, snapshotStore, logStreamWriter);
    io.verify(snapshotTrigger).forceSnapshot();
    io.verify(snapshotStore).reserveSnapshot(17L, "fresh");
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesCheckpoint(17, CheckpointType.SCHEDULED_BACKUP, "fresh"));
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verify(snapshotStore, never()).reserveLatestSnapshot(anyLong());
  }

  @Test
  void shouldReserveLatestSnapshotIfNoFreshSnapshotIsTaken() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.of("latest")));
    sender.setCheckpointInfo(17, CheckpointType.SCHEDULED_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(snapshotStore, logStreamWriter);
    io.verify(snapshotStore).reserveLatestSnapshot(17L);
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesCheckpoint(17, CheckpointType.SCHEDULED_BACKUP, "latest"));
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
  }

  @Test
  void shouldNotReserveSnapshotForMarkerCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    sender.setCheckpointInfo(17, CheckpointType.MARKER);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(any(WriteContext.class), matchesCheckpoint(17, CheckpointType.MARKER, ""));
    verify(snapshotTrigger, never()).forceSnapshot();
    verify(snapshotStore, never()).reserveLatestSnapshot(anyLong());
  }

  @Test
  void shouldWriteCheckpointAndCommandOnlyOnceSnapshotIsReserved() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot(anyLong())).thenReturn(reservation);
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);
    final var handled = sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);
    verifyNoInteractions(logStreamWriter);
    assertThat(handled).isNotDone();

    // when
    reservation.complete(Optional.of("latest"));

    // then
    assertThat(handled).isDone();
    final var io = inOrder(logStreamWriter);
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class), matchesCheckpoint(17, CheckpointType.MANUAL_BACKUP, "latest"));
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
  }

  @Test
  void shouldReleaseSnapshotReservationIfCheckpointWasCreatedWhileReserving() {
    // given - the snapshot for checkpoint 17 is being reserved
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot(anyLong())).thenReturn(reservation);
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // when - checkpoint 17 is created meanwhile, e.g. by the backup request sent to all partitions
    receiver.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);
    reservation.complete(Optional.of("latest"));

    // then - no checkpoint is written for it, so nothing will ever release the reservation
    verify(logStreamWriter, never())
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    verify(snapshotStore).releaseReservation(17L, "latest");
  }

  @Test
  void shouldNotWriteSameCheckpointTwiceBeforeItIsProcessed() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.of("latest")));
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);
    sendAndReceive(ValueType.JOB, JobIntent.COMPLETE);

    // then
    verify(logStreamWriter, times(1))
        .tryWrite(
            any(WriteContext.class), matchesCheckpoint(17, CheckpointType.MANUAL_BACKUP, "latest"));
    verify(snapshotTrigger, times(1)).forceSnapshot();
    verify(snapshotStore, times(1)).reserveLatestSnapshot(anyLong());
  }

  @Test
  void shouldReleaseSnapshotReservationIfCheckpointCannotBeWritten() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));
    when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.of("latest")));
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(snapshotStore).releaseReservation(17L, "latest");
  }

  @Test
  void shouldReleaseSnapshotReservationIfCheckpointWriteThrows() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenThrow(new IllegalStateException("expected"));
    when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.of("latest")));
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    final var handled = sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    assertThat(handled).isDone();
    assertThat(handled.isCompletedExceptionally()).isTrue();
    verify(snapshotStore).releaseReservation(17L, "latest");
  }

  @Test
  void shouldNotReleaseSnapshotReservationCarriedByWrittenCheckpoint() {
    // given - the checkpoint is written, but the command is not
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L), Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));
    when(snapshotStore.reserveLatestSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(Optional.of("latest")));
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then - the reservation is released by the backup taken for the checkpoint
    verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class), matchesCheckpoint(17, CheckpointType.MANUAL_BACKUP, "latest"));
    verify(snapshotStore, never()).releaseReservation(anyLong(), any());
  }

  @Test
  void shouldHandleMissingCheckpoints() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter, times(1))
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldCreateFirstCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    sender.setCheckpointInfo(1, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamWriter);
    io.verify(logStreamWriter, times(1))
        .tryWrite(any(WriteContext.class), matchesCheckpoint(1, CheckpointType.MANUAL_BACKUP));
    io.verify(logStreamWriter, times(1))
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    io.verifyNoMoreInteractions();
  }

  @Test
  void shouldUpdateExistingCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    receiver.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);
    sender.setCheckpointInfo(17, CheckpointType.SCHEDULED_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    final var io = inOrder(logStreamWriter);
    io.verify(logStreamWriter)
        .tryWrite(any(WriteContext.class), matchesCheckpoint(17, CheckpointType.SCHEDULED_BACKUP));
    io.verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
  }

  @Test
  void shouldNotRecreateExistingCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    receiver.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);
    sender.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
    verify(snapshotTrigger, never()).forceSnapshot();
    verify(snapshotStore, never()).reserveLatestSnapshot(anyLong());
    verify(snapshotStore, never()).releaseReservation(anyLong(), any());
  }

  @Test
  void shouldNotOverwriteNewerCheckpoint() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    receiver.setCheckpointInfo(6, CheckpointType.MANUAL_BACKUP);
    sender.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.DEPLOYMENT, DeploymentIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
    verify(snapshotTrigger, never()).forceSnapshot();
    verify(snapshotStore, never()).reserveLatestSnapshot(anyLong());
    verify(snapshotStore, never()).releaseReservation(anyLong(), any());
  }

  @Test
  void shouldNotWriteCommandIfCheckpointCreateFailed() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED), Either.right(1L));
    receiver.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verify(logStreamWriter)
        .tryWrite(
            any(WriteContext.class),
            matchesMetadata(ValueType.CHECKPOINT, CheckpointIntent.CREATE));
    verifyNoMoreInteractions(logStreamWriter);
  }

  @Test
  void shouldNotWriteCommandIfNoDiskAvailable() {
    // given
    receiver.setDiskSpaceAvailable(false);
    receiver.setCheckpointInfo(5, CheckpointType.MANUAL_BACKUP);
    sender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendAndReceive(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);

    // then
    verifyNoInteractions(logStreamWriter);
  }

  private LogAppendEntry matchesMetadata(final ValueType valueType, final Intent intent) {
    return Mockito.argThat(entry -> matchesMetadata(entry, valueType, intent));
  }

  private boolean matchesMetadata(
      final LogAppendEntry entry, final ValueType valueType, final Intent intent) {
    final var metadata = (RecordMetadata) entry.recordMetadata();
    return metadata.getValueType() == valueType && metadata.getIntent() == intent;
  }

  private LogAppendEntry matchesCheckpoint(
      final long checkpointId, final CheckpointType checkpointType, final String snapshotId) {
    return Mockito.argThat(
        entry ->
            matchesMetadata(entry, ValueType.CHECKPOINT, CheckpointIntent.CREATE)
                && entry.recordValue() instanceof final CheckpointRecord checkpoint
                && checkpoint.getCheckpointId() == checkpointId
                && checkpoint.getCheckpointType() == checkpointType
                && checkpoint.getSnapshotId().equals(snapshotId));
  }

  private LogAppendEntry matchesCheckpoint(
      final long checkpointId, final CheckpointType checkpointType) {
    return Mockito.argThat(
        entry ->
            matchesMetadata(entry, ValueType.CHECKPOINT, CheckpointIntent.CREATE)
                && entry.recordValue() instanceof final CheckpointRecord checkpoint
                && checkpoint.getCheckpointId() == checkpointId
                && checkpoint.getCheckpointType() == checkpointType);
  }

  private ActorFuture<Void> sendAndReceive(final ValueType valueType, final Intent intent) {
    sender.sendCommand(1, valueType, intent, new JobRecord());

    final var messageCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService, atLeastOnce())
        .unicast(eq(LEGACY_TOPIC_PREFIX + 1), messageCaptor.capture(), any(), any(), eq(true));
    return receiver.handleMessage(new MemberId("0"), messageCaptor.getValue());
  }
}
