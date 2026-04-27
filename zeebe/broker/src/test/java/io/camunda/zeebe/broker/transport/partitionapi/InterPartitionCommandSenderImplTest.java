/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class InterPartitionCommandSenderImplTest {

  @Mock private ClusterCommunicationService communicationService;

  private InterPartitionCommandSenderImpl sender;

  @BeforeEach
  void setUp() {
    sender = new InterPartitionCommandSenderImpl(communicationService, "test-");
  }

  @Test
  void shouldNotSendCommandWhenNoLeaderKnown() {
    // given — no leader set for partition 1

    // when
    sender.sendCommand(1, ValueType.CHECKPOINT, CheckpointIntent.CREATE, new CheckpointRecord());

    // then
    verify(communicationService, never()).unicast(any(), any(), any(), any(), any(boolean.class));
  }

  @Test
  void shouldSendCommandToZonedMemberId() {
    // given
    final var leaderId = MemberId.from("eu-west/2");
    sender.setCurrentLeader(1, leaderId);

    // when
    sender.sendCommand(1, ValueType.CHECKPOINT, CheckpointIntent.CREATE, new CheckpointRecord());

    // then
    verify(communicationService).unicast(eq("test-1"), any(), any(), eq(leaderId), eq(true));
  }

  @Test
  void shouldSendCommandToBareIntMemberId() {
    // given
    final var leaderId = MemberId.from("2");
    sender.setCurrentLeader(1, leaderId);

    // when
    sender.sendCommand(1, ValueType.CHECKPOINT, CheckpointIntent.CREATE, new CheckpointRecord());

    // then
    verify(communicationService).unicast(eq("test-1"), any(), any(), eq(leaderId), eq(true));
  }
}
