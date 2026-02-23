/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;

public final class InterPartitionCommandSenderService extends Actor
    implements InterPartitionCommandSender, CheckpointListener, TopologyPartitionListener {

  final InterPartitionCommandSenderImpl commandSender;
  final int partitionId;

  public InterPartitionCommandSenderService(
      final ClusterCommunicationService communicationService,
      final int partitionId,
      final String sendingSubjectPrefix) {
    commandSender = new InterPartitionCommandSenderImpl(communicationService, sendingSubjectPrefix);
    this.partitionId = partitionId;
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
  public void onPartitionLeaderUpdated(final int partitionId, final BrokerInfo member) {
    actor.submit(() -> commandSender.setCurrentLeader(partitionId, member.getNodeId()));
  }
}
