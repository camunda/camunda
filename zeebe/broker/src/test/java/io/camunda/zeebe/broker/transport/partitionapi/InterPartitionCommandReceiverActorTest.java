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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Runs the receiver on a real actor scheduler against a real snapshot store actor, since reserving
 * the snapshot for a checkpoint crosses actors.
 */
final class InterPartitionCommandReceiverActorTest {

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  @TempDir Path root;

  @Test
  @SuppressWarnings("unchecked")
  void shouldWriteCheckpointWithLatestSnapshotReservedOnAnotherActor() {
    // given
    final var snapshotStore =
        new FileBasedSnapshotStore(
            0, 1, root, path -> SnapshotFilesInfo.none(), new SimpleMeterRegistry());
    actorScheduler.submitActor(snapshotStore).join();
    final var snapshot = persistSnapshot(snapshotStore);

    final var logStreamWriter = mock(LogStreamWriter.class);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var communication = mock(ClusterCommunicationService.class);
    final var receiver =
        new InterPartitionCommandReceiverActor(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
            communication,
            logStreamWriter,
            snapshotStore,
            List.of(LEGACY_TOPIC_PREFIX + 1));
    actorScheduler.submitActor(receiver).join();

    final var handler = ArgumentCaptor.forClass(BiConsumer.class);
    final var executor = ArgumentCaptor.forClass(Executor.class);
    verify(communication)
        .consume(eq(LEGACY_TOPIC_PREFIX + 1), any(), handler.capture(), executor.capture());

    // when
    final byte[] message = commandWithCheckpoint(17, CheckpointType.MANUAL_BACKUP);
    executor.getValue().execute(() -> handler.getValue().accept(new MemberId("0"), message));

    // then - the checkpoint is written first, carrying the reserved snapshot, then the command
    final var written = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000).times(2))
        .tryWrite(any(WriteContext.class), written.capture());
    final var checkpoint = (CheckpointRecord) written.getAllValues().getFirst().recordValue();
    assertThat(checkpoint.getCheckpointId()).isEqualTo(17);
    assertThat(checkpoint.getSnapshotId()).isEqualTo(snapshot);
    assertThat(written.getAllValues().getLast().recordValue()).isInstanceOf(JobRecord.class);
    assertThat(snapshotStore.getReservedSnapshot(snapshot).join()).isPresent();
  }

  @Test
  void shouldHandleMessagesInOrderWhileSnapshotIsReserved() {
    // given
    final var snapshotStore = mock(PersistedSnapshotStore.class);
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot()).thenReturn(reservation);
    final var logStreamWriter = mock(LogStreamWriter.class);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var receiver = startReceiver(logStreamWriter, snapshotStore);

    // when - the second message arrives while the snapshot for the first one is reserved
    receiver.accept(commandWithCheckpoint(17, CheckpointType.MANUAL_BACKUP, ValueType.JOB));
    receiver.accept(
        commandWithCheckpoint(17, CheckpointType.MANUAL_BACKUP, ValueType.MESSAGE_SUBSCRIPTION));
    verify(snapshotStore, timeout(5_000)).reserveLatestSnapshot();
    verify(logStreamWriter, after(200).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
    reservation.complete(Optional.of("latest"));

    // then
    final var written = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000).times(3))
        .tryWrite(any(WriteContext.class), written.capture());
    assertThat(written.getAllValues())
        .extracting(entry -> (Object) entry.recordValue().getClass())
        .containsExactly(CheckpointRecord.class, JobRecord.class, MessageSubscriptionRecord.class);
    verify(snapshotStore).reserveLatestSnapshot();
  }

  @Test
  void shouldDelayMessagesWithoutCheckpointUntilSnapshotForEarlierCheckpointIsReserved() {
    // given - the first message starts a backup checkpoint, which first needs a snapshot reserved
    final var snapshotStore = mock(PersistedSnapshotStore.class);
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot()).thenReturn(reservation);
    final var logStreamWriter = mock(LogStreamWriter.class);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var receiver = startReceiver(logStreamWriter, snapshotStore);
    receiver.accept(commandWithCheckpoint(17, CheckpointType.MANUAL_BACKUP, ValueType.JOB));
    verify(snapshotStore, timeout(5_000)).reserveLatestSnapshot();

    // when - messages that need no checkpoint arrive while the snapshot is being reserved
    for (int i = 0; i < 5; i++) {
      receiver.accept(
          commandWithCheckpoint(
              CheckpointState.NO_CHECKPOINT,
              CheckpointType.MANUAL_BACKUP,
              ValueType.MESSAGE_SUBSCRIPTION));
    }

    // then - nothing is written until the snapshot is reserved
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));

    // when
    reservation.complete(Optional.of("latest"));

    // then - the checkpoint comes first, then every message in the order it was received
    final var written = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000).times(7))
        .tryWrite(any(WriteContext.class), written.capture());
    assertThat(written.getAllValues())
        .extracting(entry -> (Object) entry.recordValue().getClass())
        .containsExactly(
            CheckpointRecord.class,
            JobRecord.class,
            MessageSubscriptionRecord.class,
            MessageSubscriptionRecord.class,
            MessageSubscriptionRecord.class,
            MessageSubscriptionRecord.class,
            MessageSubscriptionRecord.class);
    assertThat(((CheckpointRecord) written.getAllValues().getFirst().recordValue()).getSnapshotId())
        .isEqualTo("latest");
  }

  @Test
  void shouldHandleNextMessageWhenHandlingPreviousOneFailed() {
    // given
    final var snapshotStore = mock(PersistedSnapshotStore.class);
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot()).thenReturn(reservation);
    final var logStreamWriter = mock(LogStreamWriter.class);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenThrow(new RuntimeException("expected"))
        .thenReturn(Either.right(1L));
    final var receiver = startReceiver(logStreamWriter, snapshotStore);
    receiver.accept(commandWithCheckpoint(17, CheckpointType.MANUAL_BACKUP, ValueType.JOB));
    receiver.accept(
        commandWithCheckpoint(-1, CheckpointType.MANUAL_BACKUP, ValueType.MESSAGE_SUBSCRIPTION));
    verify(snapshotStore, timeout(5_000)).reserveLatestSnapshot();

    // when - writing the checkpoint of the first message fails
    reservation.complete(Optional.empty());

    // then
    final var written = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000).times(2))
        .tryWrite(any(WriteContext.class), written.capture());
    assertThat(written.getAllValues().getLast().recordValue())
        .isInstanceOf(MessageSubscriptionRecord.class);
  }

  /** Starts a receiver actor and returns how to deliver a message to it, as the cluster would. */
  @SuppressWarnings("unchecked")
  private Consumer<byte[]> startReceiver(
      final LogStreamWriter logStreamWriter, final PersistedSnapshotStore snapshotStore) {
    final var communication = mock(ClusterCommunicationService.class);
    final var receiver =
        new InterPartitionCommandReceiverActor(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
            communication,
            logStreamWriter,
            snapshotStore,
            List.of(LEGACY_TOPIC_PREFIX + 1));
    actorScheduler.submitActor(receiver).join();

    final var handler = ArgumentCaptor.forClass(BiConsumer.class);
    final var executor = ArgumentCaptor.forClass(Executor.class);
    verify(communication)
        .consume(eq(LEGACY_TOPIC_PREFIX + 1), any(), handler.capture(), executor.capture());
    return message ->
        executor.getValue().execute(() -> handler.getValue().accept(new MemberId("0"), message));
  }

  private static String persistSnapshot(final FileBasedSnapshotStore store) {
    final var transientSnapshot = store.newTransientSnapshot(1, 1, 1, 0, false).get();
    transientSnapshot.take(
        path -> {
          try {
            Files.createDirectories(path);
            Files.writeString(path.resolve("file"), "content");
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    return transientSnapshot.persist().join().getId();
  }

  private static byte[] commandWithCheckpoint(
      final long checkpointId, final CheckpointType checkpointType) {
    return commandWithCheckpoint(checkpointId, checkpointType, ValueType.JOB);
  }

  private static byte[] commandWithCheckpoint(
      final long checkpointId, final CheckpointType checkpointType, final ValueType valueType) {
    final var communication = mock(ClusterCommunicationService.class);
    final var sender = new InterPartitionCommandSenderImpl(communication, LEGACY_TOPIC_PREFIX);
    sender.setCurrentLeader(1, MemberId.from("1"));
    sender.setCheckpointInfo(checkpointId, checkpointType);
    if (valueType == ValueType.JOB) {
      sender.sendCommand(1, ValueType.JOB, JobIntent.COMPLETE, new JobRecord());
    } else {
      sender.sendCommand(
          1,
          ValueType.MESSAGE_SUBSCRIPTION,
          MessageSubscriptionIntent.CORRELATE,
          new MessageSubscriptionRecord().setProcessInstanceKey(1).setElementInstanceKey(1));
    }

    final var message = ArgumentCaptor.forClass(byte[].class);
    verify(communication)
        .unicast(eq(LEGACY_TOPIC_PREFIX + 1), message.capture(), any(), any(), eq(true));
    return message.getValue();
  }
}
