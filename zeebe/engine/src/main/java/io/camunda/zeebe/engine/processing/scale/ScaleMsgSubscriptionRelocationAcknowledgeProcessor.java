/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.agrona.collections.MutableBoolean;

public class ScaleMsgSubscriptionRelocationAcknowledgeProcessor
    implements TypedRecordProcessor<ScaleRecord> {

  MessageSubscriptionState messageSubscriptionState;
  private final RelocationState relocationState;
  private final StateWriter stateWriter;

  public ScaleMsgSubscriptionRelocationAcknowledgeProcessor(
      final ProcessingState processingState, final Writers writers) {
    this.relocationState = processingState.getRelocationState();
    messageSubscriptionState = processingState.getMessageSubscriptionState();
    this.stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    // on processing ACKNOWLEDGE -> write MOVED, if this is the last subscription to be
    // moved, then write  MSG_SUBSCRIPTION_RELOCATION_COMPLETED for this correlationKey

    stateWriter.appendFollowUpEvent(
        record.getKey(),
        ScaleIntent.MSG_SUBSCRIPTION_RELOCATION_MOVED,
        record.getValue()); // removes the message subscription from state

    final var isLastSubscription = new MutableBoolean(true);
    messageSubscriptionState.visitSubscriptions(
        record.getValue().getCorrelationKey(),
        messageSubscription -> {
          isLastSubscription.set(false);
          return false;
        });

    if (isLastSubscription.get()) {
      // TODO: this should start relocation of published messages.
      stateWriter.appendFollowUpEvent(
          record.getKey(),
          ScaleIntent.MSG_SUBSCRIPTION_RELOCATION_COMPLETED,
          record.getValue()); // remove relocation state for this correlationKey
    }

    if (!relocationState.isRelocationInProgressForAnyCorrelationKey()) {
      stateWriter.appendFollowUpEvent(
          record.getKey(),
          ScaleIntent.RELOCATE_MESSAGES_COMPLETED,
          record.getValue()); // Update routing info

      // TODO: notify other partitions to update routing info (partial)
    }
  }
}
