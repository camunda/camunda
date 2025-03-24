/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class CommandDistributionAcknowledgeProcessor
    implements TypedRecordProcessor<CommandDistributionRecord> {

  private static final String ERROR_PENDING_DISTRIBUTION_NOT_FOUND =
      """
      Expected to find pending distribution with key %d for partition %d, but no pending \
      distribution was found.""";

  private final CommandDistributionBehavior commandDistributionBehavior;
  private final DistributionState distributionState;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public CommandDistributionAcknowledgeProcessor(
      final CommandDistributionBehavior commandDistributionBehavior,
      final DistributionState distributionState,
      final Writers writers) {
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.distributionState = distributionState;
    commandWriter = writers.command();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<CommandDistributionRecord> record) {
    final var distributionKey = record.getKey();
    final var recordValue = record.getValue();
    final var partitionId = recordValue.getPartitionId();

    if (!distributionState.hasPendingDistribution(distributionKey, partitionId)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(ERROR_PENDING_DISTRIBUTION_NOT_FOUND, distributionKey, partitionId));
      return;
    }

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.ACKNOWLEDGED, recordValue);

    final var queueId = distributionState.getQueueIdForDistribution(distributionKey);
    queueId.ifPresent(
        queue -> commandDistributionBehavior.distributeNextInQueue(queue, partitionId));

    if (!distributionState.hasPendingDistribution(distributionKey)) {
      final var finishRecord =
          new CommandDistributionRecord()
              .setPartitionId(record.getPartitionId())
              .setValueType(recordValue.getValueType())
              .setIntent(recordValue.getIntent());
      queueId.ifPresent(finishRecord::setQueueId);

      commandWriter.appendFollowUpCommand(
          distributionKey, CommandDistributionIntent.FINISH, finishRecord);
    }
  }
}
