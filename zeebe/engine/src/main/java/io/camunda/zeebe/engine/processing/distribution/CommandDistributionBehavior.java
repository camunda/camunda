/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The network communication between the partitions is unreliable. To allow communication between
 * partitions in a reliable way, we've built command distribution: a way for partitions to send,
 * receive, and acknowledge (or retry sending) commands between partitions.
 *
 * <p>This behavior allows distributing a command to other partitions, and for those receiving
 * partitions to acknowledge the distributed commands back to the partition that started.
 *
 * @see <a
 *     href="https://github.com/camunda/camunda/blob/main/zeebe/docs/generalized_distribution.md">
 *     generalized_distribution.md</a>
 */
public final class CommandDistributionBehavior {

  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final InterPartitionCommandSender interPartitionCommandSender;

  private final int currentPartitionId;
  private final List<Integer> otherPartitions;

  // Records are expensive to construct, so we create them once and reuse them
  private final CommandDistributionRecord commandDistributionStarted =
      new CommandDistributionRecord();
  private final CommandDistributionRecord commandDistributionDistributing =
      new CommandDistributionRecord();
  private final CommandDistributionRecord commandDistributionAcknowledge =
      new CommandDistributionRecord();

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
   * Distributes a command to all other partitions.
   *
   * @param distributionKey the key to identify this unique command distribution. The key used for
   *     three purposes: storing the pending distribution as the key of distributed command,
   *     identifying for the distributed command's partition, and correlating the ACKNOWLEDGE
   *     command to the pending distribution. This can be a newly generated key, or the key
   *     identifying the entity that's being distributed. Please note that it must be unique for
   *     command distribution. Don't reuse the key to distribute another command until the previous
   *     command distribution has been completed.
   * @param command the command to distribute
   */
  public <T extends UnifiedRecordValue> void distributeCommand(
      final long distributionKey, final TypedRecord<T> command) {
    distributeCommand(distributionKey, command, otherPartitions);
  }

  /**
   * Distributes a command to the specified partitions.
   *
   * @param distributionKey the key to identify this unique command distribution. The key is used to
   *     store the pending distribution, as the key of distributed command, to identify the
   *     distributing partition when processing the distributed command, and as the key to correlate
   *     the ACKNOWLEDGE command to the pending distribution. This can be a newly generated key, or
   *     the key identifying the entity that's being distributed. Please note that it must be unique
   *     for command distribution. Don't reuse the key to distribute another command. Don't reuse
   *     the key to distribute another command until the previous command distribution has been
   *     completed.
   * @param command the command to distribute
   * @param partitions the partitions to distribute the command to
   */
  public <T extends UnifiedRecordValue> void distributeCommand(
      final long distributionKey, final TypedRecord<T> command, final List<Integer> partitions) {
    distributeCommand(
        distributionKey,
        command.getValueType(),
        command.getIntent(),
        command.getValue(),
        partitions);
  }

  /**
   * Distributes a command to the specified partitions.
   *
   * @param distributionKey the key to identify this unique command distribution. The key is used to
   *     store the pending distribution, as the key of distributed command, to identify the
   *     distributing partition when processing the distributed command, and as the key to correlate
   *     the ACKNOWLEDGE command to the pending distribution. This can be a newly generated key, or
   *     the key identifying the entity that's being distributed. Please note that it must be unique
   *     for command distribution. Don't reuse the key to distribute another command. Don't reuse
   *     the key to distribute another command until the previous command distribution has been
   *     completed.
   * @param valueType the type of the command to distribute
   * @param intent the intent of the command to distribute
   * @param value the value of the command to distribute
   * @param partitions the partitions to distribute the command to
   */
  public <T extends UnifiedRecordValue> void distributeCommand(
      final long distributionKey,
      final ValueType valueType,
      final Intent intent,
      final T value,
      final List<Integer> partitions) {
    if (partitions.isEmpty()) {
      return;
    }

    commandDistributionStarted.reset();

    final var distributionRecord =
        commandDistributionStarted
            .setPartitionId(currentPartitionId)
            .setValueType(valueType)
            .setIntent(intent)
            .setCommandValue(value);

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.STARTED, distributionRecord);

    partitions.forEach(
        (partition) -> distributeToPartition(partition, distributionRecord, distributionKey));
  }

  private <T extends UnifiedRecordValue> void distributeToPartition(
      final int partition,
      final CommandDistributionRecord distributionRecord,
      final long distributionKey) {
    final var valueType = distributionRecord.getValueType();
    final var intent = distributionRecord.getIntent();

    commandDistributionDistributing.reset();

    // We don't need the actual record in the DISTRIBUTING event applier. In order to prevent
    // reaching the max message size we don't set the record value here.
    stateWriter.appendFollowUpEvent(
        distributionKey,
        CommandDistributionIntent.DISTRIBUTING,
        commandDistributionDistributing
            .setPartitionId(partition)
            .setValueType(valueType)
            .setIntent(intent));

    // This getter makes a hard copy of the command value, which we need to send the command to the
    // other partition in a side effect. It does not appear to be possible to reuse a single
    // instance for distributing to all partitions in the form of a method parameter, but it's not
    // fully clear why that leads to problems. We suspect that it's because the command value is a
    // mutable object, and it is somehow modified before the side effect is executed. Instead, we
    // have to copy this value for every partition.
    final var commandValue = distributionRecord.getCommandValue();

    sideEffectWriter.appendSideEffect(
        () -> {
          interPartitionCommandSender.sendCommand(
              partition, valueType, intent, distributionKey, commandValue);
          return true;
        });
  }

  /**
   * Acknowledges that a command was distributed to another partition successfully.
   *
   * @param command the command that was distributed
   */
  public <T extends UnifiedRecordValue> void acknowledgeCommand(final TypedRecord<T> command) {
    final long distributionKey = command.getKey();

    commandDistributionAcknowledge.reset();

    final var distributionRecord =
        commandDistributionAcknowledge
            .setPartitionId(currentPartitionId)
            .setValueType(command.getValueType())
            .setIntent(command.getIntent());

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
