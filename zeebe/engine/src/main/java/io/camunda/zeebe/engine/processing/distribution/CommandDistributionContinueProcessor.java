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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

@ExcludeAuthorizationCheck
public class CommandDistributionContinueProcessor
    implements TypedRecordProcessor<CommandDistributionRecord> {

  private final DistributionState distributionState;
  private final TypedCommandWriter commandWriter;
  private final TypedEventWriter stateWriter;

  public CommandDistributionContinueProcessor(
      final DistributionState distributionState, final Writers writers) {
    this.distributionState = distributionState;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<CommandDistributionRecord> record) {
    final var key = record.getKey();
    final var distributionRecord = record.getValue();
    final var queue = distributionRecord.getQueueId();

    final var continuationRecord = distributionState.getContinuationRecord(queue, key);
    final var intent = continuationRecord.getIntent();
    final var continuationCommand = continuationRecord.getCommandValue();
    commandWriter.appendFollowUpCommand(key, intent, continuationCommand);

    stateWriter.appendFollowUpEvent(key, CommandDistributionIntent.CONTINUED, distributionRecord);
  }
}
