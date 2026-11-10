/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The sender chooses between the network and the mailbox by the leaders it knows. */
final class InterPartitionCommandSenderMailboxTest {

  private final ClusterCommunicationService communicationService =
      mock(ClusterCommunicationService.class);
  private final InterPartitionMailbox mailbox = mock(InterPartitionMailbox.class);

  @Test
  void shouldUseTheMailboxWhenNoLeaderIsKnown() {
    // given
    final var sender = sender(null);

    // when
    sendCheckpointCommand(sender);

    // then
    verify(mailbox).send(eq(1), any(byte[].class));
    verify(communicationService, never()).unicast(any(), any(), any(), any(), any(boolean.class));
  }

  @Test
  void shouldUseTheNetworkWhenTheLeaderIsKnown() {
    // given
    final var sender = sender(null);
    final var leader = MemberId.from("eu", 2);
    sender.setCurrentLeader(1, leader);

    // when
    sendCheckpointCommand(sender);

    // then
    verify(communicationService).unicast(eq("test-1"), any(), any(), eq(leader), eq(true));
    verify(mailbox, never()).send(anyInt(), any(byte[].class));
  }

  @Test
  void shouldSendTheSameBytesThroughTheMailboxAsOverTheNetwork() {
    // given
    final var networkSender = sender(null);
    networkSender.setCurrentLeader(1, MemberId.from("eu", 2));
    networkSender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);
    final var mailboxSender = sender(null);
    mailboxSender.setCheckpointInfo(17, CheckpointType.MANUAL_BACKUP);

    // when
    sendCheckpointCommand(networkSender);
    sendCheckpointCommand(mailboxSender);

    // then
    final var overNetwork = ArgumentCaptor.forClass(byte[].class);
    verify(communicationService)
        .unicast(any(), overNetwork.capture(), any(), any(), any(boolean.class));
    final var inMailbox = ArgumentCaptor.forClass(byte[].class);
    verify(mailbox).send(eq(1), inMailbox.capture());
    assertThat(inMailbox.getValue()).isEqualTo(overNetwork.getValue());
  }

  @Test
  void shouldUseTheMailboxForALeaderInAnotherZoneWhenZonesAreSimulated() {
    // given
    final var sender = sender("a");
    sender.setCurrentLeader(1, MemberId.from("b", 1));

    // when
    sendCheckpointCommand(sender);

    // then
    verify(mailbox).send(eq(1), any(byte[].class));
    verify(communicationService, never()).unicast(any(), any(), any(), any(), any(boolean.class));
  }

  @Test
  void shouldUseTheNetworkForALeaderInTheOwnZoneWhenZonesAreSimulated() {
    // given
    final var sender = sender("a");
    final var leader = MemberId.from("a", 1);
    sender.setCurrentLeader(1, leader);

    // when
    sendCheckpointCommand(sender);

    // then
    verify(communicationService).unicast(eq("test-1"), any(), any(), eq(leader), eq(true));
    verify(mailbox, never()).send(anyInt(), any(byte[].class));
  }

  @Test
  void shouldNotSendWithoutAMailboxWhenNoLeaderIsKnown() {
    // given
    final var sender = new InterPartitionCommandSenderImpl(communicationService, "test-");

    // when
    sendCheckpointCommand(sender);

    // then
    verify(communicationService, never()).unicast(any(), any(), any(), any(), any(boolean.class));
    verify(mailbox, never()).send(anyInt(), any(byte[].class));
  }

  private InterPartitionCommandSenderImpl sender(final String simulatedZone) {
    return new InterPartitionCommandSenderImpl(
        communicationService, "test-", mailbox, simulatedZone);
  }

  private static void sendCheckpointCommand(final InterPartitionCommandSenderImpl sender) {
    sender.sendCommand(1, ValueType.CHECKPOINT, CheckpointIntent.CREATE, new CheckpointRecord());
  }
}
