/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class CommandDistributionAcknowledgeProcessor
    implements TypedRecordProcessor<CommandDistributionRecord> {

  private static final String ERROR_PENDING_DISTRIBUTION_NOT_FOUND =
      """
      Expected to find pending distribution with key %d for partition %d, but no pending \
      distribution was found.""";
  private static final CommandDistributionRecord EMPTY_DISTRIBUTION_RECORD =
      new CommandDistributionRecord();

  private final DistributionState distributionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public CommandDistributionAcknowledgeProcessor(
      final DistributionState distributionState, final Writers writers) {
    this.distributionState = distributionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<CommandDistributionRecord> record) {
    final var distributionKey = record.getKey();
    final var partitionId = record.getValue().getPartitionId();

    if (!distributionState.hasPendingDistribution(distributionKey, partitionId)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(ERROR_PENDING_DISTRIBUTION_NOT_FOUND, distributionKey, partitionId));
    }

    stateWriter.appendFollowUpEvent(
        distributionKey, CommandDistributionIntent.ACKNOWLEDGED, record.getValue());

    if (!distributionState.hasPendingDistribution(distributionKey)) {
      // We write an empty command here as a distribution could contain a lot of data. Because of
      // this we could exceed the max message size. As we only need the distributionKey in the
      // FINISHED event applier an empty record will suffice here.
      stateWriter.appendFollowUpEvent(
          distributionKey,
          CommandDistributionIntent.FINISHED,
          new CommandDistributionRecord().setPartitionId(record.getPartitionId()));
    }
  }
}
