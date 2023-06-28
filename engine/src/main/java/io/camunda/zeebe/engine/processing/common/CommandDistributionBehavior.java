/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.stream.IntStream;

public final class CommandDistributionBehavior {

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final List<Integer> otherPartitions;
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final int currentPartitionId;

  public CommandDistributionBehavior(
      final Writers writers,
      final int currentPartition,
      final int partitionsCount,
      final InterPartitionCommandSender partitionCommandSender) {
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    interPartitionCommandSender = partitionCommandSender;
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != currentPartition)
            .boxed()
            .toList();
    currentPartitionId = currentPartition;
  }

  /**
   * Distributes a command to the other partitions
   *
   * @param distributionKey the key which is used for the distribution. This could either be a new
   *     key, but it could also be the key of the entity that's getting distributed.
   * @param command the command that needs to be distributed
   */
  public <T extends UnifiedRecordValue> void distributeCommand(
      final long distributionKey, final TypedRecord<T> command) {
    if (otherPartitions.isEmpty()) {
      return;
    }

    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent())
            .setCommandValue(command.getValue());

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.STARTED, distributionRecord);

    otherPartitions.forEach(
        (partition) ->
            distributeToPartition(command, partition, distributionRecord, distributionKey));
  }

  private <T extends UnifiedRecordValue> void distributeToPartition(
      final TypedRecord<T> command,
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long distributionKey) {
    final var valueType = distributionRecord.getValueType();
    final var commandValue = distributionRecord.getCommandValue();
    // We don't need the actual record in the DISTRIBUTING event applier. In order to prevent
    // reaching the max message size we don't set the record value here.
    stateWriter.appendFollowUpEvent(
        distributionKey,
        CommandDistributionIntent.DISTRIBUTING,
        new CommandDistributionRecord()
            .setPartitionId(partition)
            .setValueType(valueType)
            .setIntent(command.getIntent()));

    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              partition,
              command.getValueType(),
              command.getIntent(),
              distributionKey,
              commandValue);
          return true;
        });
  }

  public <T extends UnifiedRecordValue> void acknowledgeCommand(
      final long distributionKey, final TypedRecord<T> command) {
    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent());

    final int receiverPartitionId = Protocol.decodePartitionId(command.getKey());
    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              receiverPartitionId,
              ValueType.COMMAND_DISTRIBUTION,
              CommandDistributionIntent.ACKNOWLEDGE,
              distributionKey,
              distributionRecord);
          return true;
        });
  }
}
