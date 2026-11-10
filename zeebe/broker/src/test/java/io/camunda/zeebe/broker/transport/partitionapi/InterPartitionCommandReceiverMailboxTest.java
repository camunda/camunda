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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
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
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

/** Messages from the mailbox go through the same receiver as messages from the network. */
final class InterPartitionCommandReceiverMailboxTest {

  private static final MemberId SENDER = MemberId.from("1");

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  private final LogStreamWriter logStreamWriter = mock(LogStreamWriter.class);
  private final PersistedSnapshotStore snapshotStore = mock(PersistedSnapshotStore.class);

  @Test
  void shouldReportThatTheCommandIsInTheLog() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var receiver = startReceiver();

    // when
    final var written =
        receiver
            .handleMailboxMessage(SENDER, command(CheckpointState.NO_CHECKPOINT, ValueType.JOB))
            .join(5, TimeUnit.SECONDS);

    // then
    assertThat(written).isTrue();
    verify(logStreamWriter).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldReportThatTheCommandIsNotInTheLogWhenTheLogRejectsIt() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.left(WriteFailure.WRITE_LIMIT_EXHAUSTED));
    final var receiver = startReceiver();

    // when
    final var written =
        receiver
            .handleMailboxMessage(SENDER, command(CheckpointState.NO_CHECKPOINT, ValueType.JOB))
            .join(5, TimeUnit.SECONDS);

    // then
    assertThat(written).isFalse();
  }

  @Test
  void shouldReportThatTheCommandIsNotInTheLogWhenThereIsNoDiskSpace() {
    // given
    final var receiver = startReceiver();
    receiver.onDiskSpaceNotAvailable();

    // when
    final var written =
        receiver
            .handleMailboxMessage(SENDER, command(CheckpointState.NO_CHECKPOINT, ValueType.JOB))
            .join(5, TimeUnit.SECONDS);

    // then
    assertThat(written).isFalse();
    verify(logStreamWriter, never()).tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldFailTheResultWhenTheLogThrows() {
    // given
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenThrow(new IllegalStateException("expected"));
    final var receiver = startReceiver();

    // when
    final var result =
        receiver.handleMailboxMessage(
            SENDER, command(CheckpointState.NO_CHECKPOINT, ValueType.JOB));

    // then
    await().atMost(Duration.ofSeconds(5)).until(result::isDone);
    assertThat(result.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldWriteAMailboxMessageAfterTheNetworkMessagesThatCameBefore() {
    // given - a network message needs a snapshot for its checkpoint, which delays what follows
    final var reservation = new CompletableActorFuture<Optional<String>>();
    when(snapshotStore.reserveLatestSnapshot(anyLong())).thenReturn(reservation);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));
    final var communication = mock(ClusterCommunicationService.class);
    final var receiver = startReceiver(communication);
    final var network = networkDelivery(communication);
    network.accept(command(17, ValueType.JOB));
    verify(snapshotStore, timeout(5_000)).reserveLatestSnapshot(anyLong());

    // when - a message from the mailbox arrives while the snapshot is reserved
    final var result =
        receiver.handleMailboxMessage(
            SENDER, command(CheckpointState.NO_CHECKPOINT, ValueType.MESSAGE_SUBSCRIPTION));

    // then - it waits
    verify(logStreamWriter, after(300).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
    assertThat(result).isNotDone();

    // when
    reservation.complete(Optional.of("latest"));

    // then - the checkpoint and the network command come first
    assertThat(result.join(5, TimeUnit.SECONDS)).isTrue();
    final var written = ArgumentCaptor.forClass(LogAppendEntry.class);
    verify(logStreamWriter, timeout(5_000).times(3))
        .tryWrite(any(WriteContext.class), written.capture());
    assertThat(written.getAllValues())
        .extracting(entry -> (Object) entry.recordValue().getClass())
        .containsExactly(CheckpointRecord.class, JobRecord.class, MessageSubscriptionRecord.class);
  }

  private InterPartitionCommandReceiverActor startReceiver() {
    return startReceiver(mock(ClusterCommunicationService.class));
  }

  private InterPartitionCommandReceiverActor startReceiver(
      final ClusterCommunicationService communication) {
    final var checkpointState = mock(CheckpointState.class);
    when(checkpointState.getLatestCheckpointId()).thenReturn(CheckpointState.NO_CHECKPOINT);
    final var receiver =
        new InterPartitionCommandReceiverActor(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
            communication,
            logStreamWriter,
            snapshotStore,
            () -> CompletableActorFuture.completed(null),
            checkpointState,
            List.of(LEGACY_TOPIC_PREFIX + 1));
    actorScheduler.submitActor(receiver).join();
    return receiver;
  }

  /** How the cluster delivers a message from the network to the receiver. */
  @SuppressWarnings("unchecked")
  private static Consumer<byte[]> networkDelivery(final ClusterCommunicationService communication) {
    final var handler = ArgumentCaptor.forClass(BiConsumer.class);
    final var executor = ArgumentCaptor.forClass(Executor.class);
    verify(communication)
        .consume(eq(LEGACY_TOPIC_PREFIX + 1), any(), handler.capture(), executor.capture());
    return message -> executor.getValue().execute(() -> handler.getValue().accept(SENDER, message));
  }

  private static byte[] command(final long checkpointId, final ValueType valueType) {
    final var communication = mock(ClusterCommunicationService.class);
    final var sender = new InterPartitionCommandSenderImpl(communication, LEGACY_TOPIC_PREFIX);
    sender.setCurrentLeader(1, MemberId.from("1"));
    sender.setCheckpointInfo(checkpointId, CheckpointType.MANUAL_BACKUP);
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
