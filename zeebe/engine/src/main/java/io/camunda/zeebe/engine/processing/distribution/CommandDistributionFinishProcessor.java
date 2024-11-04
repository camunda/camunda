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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;

@ExcludeAuthorizationCheck
public class CommandDistributionFinishProcessor
    implements TypedRecordProcessor<CommandDistributionRecord> {
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;

  public CommandDistributionFinishProcessor(
      final Writers writers, final CommandDistributionBehavior commandDistributionBehavior) {
    stateWriter = writers.state();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<CommandDistributionRecord> record) {
    final var distributionRecord = record.getValue();
    Optional.ofNullable(distributionRecord.getQueueId())
        .ifPresent(commandDistributionBehavior::continueAfterQueue);
    stateWriter.appendFollowUpEvent(
        record.getKey(), CommandDistributionIntent.FINISHED, distributionRecord);
  }
}
