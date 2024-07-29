/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ScaleRelocateMessageSubscriptionApplyProcessor
    implements TypedRecordProcessor<ScaleRecord> {
  private final MessageSubscriptionState subscriptionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;

  public ScaleRelocateMessageSubscriptionApplyProcessor(
      final MessageSubscriptionState subscriptionState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final Writers writers) {
    this.subscriptionState = subscriptionState;
    this.commandDistributionBehavior = commandDistributionBehavior;
    rejectionWriter = writers.rejection();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    final var subscriptionRecord = record.getValue().getMessageSubscriptionRecord();

    if (subscriptionState.existSubscriptionForElementInstance(
        subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer())) {
      rejectionWriter.appendRejection(
          record, RejectionType.INVALID_STATE, "Already relocated subscription");
    } else {
      stateWriter.appendFollowUpEvent(
          record.getKey(), MessageSubscriptionIntent.CREATED, subscriptionRecord);
    }

    // TODO: Do we need distribution key here? Or is it set on the record?
    commandDistributionBehavior.acknowledgeCommand(record);
  }
}
