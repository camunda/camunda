/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.raft.RaftServer.Role;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.mailbox.MailboxSetup;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandReceiverActor;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderImpl;
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandSenderService;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The transition step uses the mailbox only if the broker has one, and only for its default tenant.
 */
final class InterPartitionCommandServiceStepMailboxTest {

  private static final PartitionId PARTITION =
      new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1);
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  @RegisterExtension final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final LogStreamWriter logStreamWriter = mock(LogStreamWriter.class);
  private final PartitionTransitionContext context = mock(PartitionTransitionContext.class);
  private final AtomicReference<InterPartitionCommandReceiverActor> receiver =
      new AtomicReference<>();
  private final AtomicReference<InterPartitionCommandSenderService> sender =
      new AtomicReference<>();
  private final InterPartitionCommandServiceStep step = new InterPartitionCommandServiceStep();
  private ControlActor controlActor;

  @BeforeEach
  void setUp() {
    controlActor = new ControlActor();
    actorScheduler.submitActor(controlActor).join();

    final var logStream = mock(LogStream.class);
    when(logStream.newLogStreamWriter()).thenReturn(logStreamWriter);
    when(logStreamWriter.tryWrite(any(WriteContext.class), any(LogAppendEntry.class)))
        .thenReturn(Either.right(1L));

    when(context.partitionId()).thenReturn(PARTITION);
    when(context.getMemberId()).thenReturn(BrokerMemberId.from("a", 0));
    when(context.getConcurrencyControl()).thenReturn(controlActor.control());
    when(context.getActorSchedulingService()).thenReturn(actorScheduler.get());
    when(context.getLogStream()).thenReturn(logStream);
    when(context.getBrokerCfg()).thenReturn(new BrokerCfg());
    when(context.getZeebeDb()).thenReturn(mock(ZeebeDb.class));
    when(context.getClusterCommunicationService())
        .thenReturn(mock(ClusterCommunicationService.class));
    when(context.getPersistedSnapshotStore()).thenReturn(mock(PersistedSnapshotStore.class));
    when(context.getCheckpointProcessor()).thenReturn(mock(CheckpointRecordsProcessor.class));
    when(context.getTopologyManager()).thenReturn(mock(TopologyManager.class));
    doAnswer(
            invocation -> {
              receiver.set(invocation.getArgument(0));
              return null;
            })
        .when(context)
        .setPartitionCommandReceiver(any());
    doAnswer(
            invocation -> {
              sender.set(invocation.getArgument(0));
              return null;
            })
        .when(context)
        .setPartitionCommandSender(any());
    when(context.getPartitionCommandReceiver()).thenAnswer(invocation -> receiver.get());
    when(context.getPartitionCommandSender()).thenAnswer(invocation -> sender.get());
  }

  @Test
  void shouldWriteTheCommandsInTheInboxToTheLogOfALeader() {
    // given
    when(context.getMailboxSetup()).thenReturn(Optional.of(MailboxSetup.of(store, CONFIG, null)));
    store.insert(inboxMessage(PARTITION.number(), 1));

    // when
    transition(() -> step.transitionTo(context, 1, Role.LEADER));

    // then
    verify(logStreamWriter, timeout(5_000))
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldSendCommandsToPartitionsWithoutAKnownLeaderThroughTheMailbox() {
    // given
    when(context.getMailboxSetup()).thenReturn(Optional.of(MailboxSetup.of(store, CONFIG, null)));
    transition(() -> step.transitionTo(context, 1, Role.LEADER));

    // when
    sender.get().sendCommand(2, ValueType.JOB, JobIntent.COMPLETE, new JobRecord());

    // then
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(store.claim(2, "next-leader", 10, Duration.ofMinutes(1)))
                    .hasSize(1)
                    .allSatisfy(
                        message ->
                            assertThat(message.kind())
                                .isEqualTo(MessageKind.INTER_PARTITION_COMMAND)));
  }

  @Test
  void shouldStopTakingCommandsFromTheInboxWhenTheNodeStopsLeading() {
    // given
    when(context.getMailboxSetup()).thenReturn(Optional.of(MailboxSetup.of(store, CONFIG, null)));
    transition(() -> step.transitionTo(context, 1, Role.LEADER));
    transition(() -> step.prepareTransition(context, 2, Role.FOLLOWER));

    // when
    store.insert(inboxMessage(PARTITION.number(), 1));

    // then
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
  }

  @Test
  void shouldNotUseTheMailboxWithoutASetup() {
    // given
    when(context.getMailboxSetup()).thenReturn(Optional.empty());
    store.insert(inboxMessage(PARTITION.number(), 1));
    transition(() -> step.transitionTo(context, 1, Role.LEADER));

    // when
    sender.get().sendCommand(2, ValueType.JOB, JobIntent.COMPLETE, new JobRecord());

    // then
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
    assertThat(store.claim(2, "next-leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  @Test
  void shouldNotUseTheMailboxForAnotherPhysicalTenant() {
    // given
    when(context.partitionId()).thenReturn(new PartitionId("tenant-x", 1));
    when(context.getMailboxSetup()).thenReturn(Optional.of(MailboxSetup.of(store, CONFIG, null)));
    store.insert(inboxMessage(PARTITION.number(), 1));
    transition(() -> step.transitionTo(context, 1, Role.LEADER));

    // when
    sender.get().sendCommand(2, ValueType.JOB, JobIntent.COMPLETE, new JobRecord());

    // then
    verify(logStreamWriter, after(500).never())
        .tryWrite(any(WriteContext.class), any(LogAppendEntry.class));
    assertThat(store.claim(2, "next-leader", 10, Duration.ofMinutes(1))).isEmpty();
  }

  /** A message as another partition's sender would put it into the inbox. */
  private static NewInboxMessage inboxMessage(final int partition, final long requestId) {
    final var encoded = new AtomicReference<byte[]>();
    final var encoder =
        new InterPartitionCommandSenderImpl(
            mock(ClusterCommunicationService.class),
            "test-",
            (receiverPartition, message) -> encoded.set(message),
            null);
    encoder.sendCommand(partition, ValueType.JOB, JobIntent.COMPLETE, new JobRecord());
    return new NewInboxMessage(
        partition, MessageKind.INTER_PARTITION_COMMAND, "sender-1", requestId, encoded.get());
  }

  /** Runs a step on the actor and waits for its result. A partition runs its steps this way. */
  private void transition(final Supplier<ActorFuture<Void>> step) {
    controlActor.call(step).join().join();
  }

  /** Gives the test a real concurrency control, as a partition has. */
  private static final class ControlActor extends Actor {
    ConcurrencyControl control() {
      return actor;
    }

    ActorFuture<ActorFuture<Void>> call(final Supplier<ActorFuture<Void>> step) {
      return actor.call(step::get);
    }
  }
}
