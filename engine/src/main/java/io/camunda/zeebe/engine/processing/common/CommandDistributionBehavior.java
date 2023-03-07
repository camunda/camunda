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
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import java.util.stream.IntStream;

public final class CommandDistributionBehavior {

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final List<Integer> otherPartitions;
  private final InterPartitionCommandSender interPartitionCommandSender;
  private final KeyGenerator keyGenerator;
  private final int currentPartitionId;

  public CommandDistributionBehavior(
      final Writers writers,
      final int currentPartition,
      final int partitionsCount,
      final InterPartitionCommandSender partitionCommandSender,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    sideEffectWriter = writers.sideEffect();
    interPartitionCommandSender = partitionCommandSender;
    this.keyGenerator = keyGenerator;
    otherPartitions =
        IntStream.range(Protocol.START_PARTITION_ID, Protocol.START_PARTITION_ID + partitionsCount)
            .filter(partition -> partition != currentPartition)
            .boxed()
            .toList();
    currentPartitionId = currentPartition;
  }

  public <T extends UnifiedRecordValue> void distributeCommand(final TypedRecord<T> command) {
    final var distributionRecord =
        new CommandDistributionRecord()
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setRecordValue(command.getValue());

    stateWriter.appendFollowUpEvent(
        command.getKey(), CommandDistributionIntent.STARTED, distributionRecord);

    final long key = keyGenerator.nextKey();
    otherPartitions.forEach(
        (partition) -> distributeToPartition(command, partition, distributionRecord, key));
  }

  private <T extends UnifiedRecordValue> void distributeToPartition(
      final TypedRecord<T> command,
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long key) {
    // We don't need the actual record in the DISTRIBUTING event applier. In order to prevent
    // reaching the max message size we don't set the record value here.
    stateWriter.appendFollowUpEvent(
        command.getKey(),
        CommandDistributionIntent.DISTRIBUTING,
        new CommandDistributionRecord()
            .setPartitionId(partition)
            .setValueType(distributionRecord.getValueType()));

    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              partition,
              command.getValueType(),
              command.getIntent(),
              key,
              distributionRecord.getCommandValue());
          return true;
        });
  }

  public <T extends UnifiedRecordValue> void acknowledgeCommand(final long distributionKey) {
    final var distributionRecord =
        new CommandDistributionRecord().setPartitionId(currentPartitionId);

    final int receiverPartitionId = Protocol.decodePartitionId(distributionKey);
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
