/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import org.jspecify.annotations.Nullable;

public final class InterPartitionCommandSenderService extends Actor
    implements InterPartitionCommandSender, CheckpointListener, TopologyPartitionListener {

  final InterPartitionCommandSenderImpl commandSender;

  public InterPartitionCommandSenderService(
      final PartitionId partitionId,
      final ClusterCommunicationService communicationService,
      final String sendingSubjectPrefix) {
    this(partitionId, communicationService, sendingSubjectPrefix, null, null);
  }

  /**
   * @param mailbox the path for partitions that are out of reach, or null to not use a mailbox
   * @param simulatedZone see {@link InterPartitionCommandSenderImpl}. Null in a real deployment.
   */
  public InterPartitionCommandSenderService(
      final PartitionId partitionId,
      final ClusterCommunicationService communicationService,
      final String sendingSubjectPrefix,
      final @Nullable InterPartitionMailbox mailbox,
      final @Nullable String simulatedZone) {
    super("InterPartitionCommandSenderService", partitionId);
    commandSender =
        new InterPartitionCommandSenderImpl(
            communicationService, sendingSubjectPrefix, mailbox, simulatedZone);
  }

  @Override
  public void onNewCheckpointCreated(final long checkpointId, final CheckpointType checkpointType) {
    actor.submit(() -> commandSender.setCheckpointInfo(checkpointId, checkpointType));
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command) {
    actor.submit(() -> commandSender.sendCommand(receiverPartitionId, valueType, intent, command));
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command) {
    actor.submit(
        () ->
            commandSender.sendCommand(receiverPartitionId, valueType, intent, recordKey, command));
  }

  @Override
  public void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command,
      final AuthInfo authInfo) {
    actor.submit(
        () ->
            commandSender.sendCommand(
                receiverPartitionId, valueType, intent, recordKey, command, authInfo));
  }

  @Override
  public void onPartitionLeaderUpdated(final int leaderPartitionId, final BrokerMemberId leaderId) {
    actor.submit(() -> commandSender.setCurrentLeader(leaderPartitionId, leaderId.memberId()));
  }
}
