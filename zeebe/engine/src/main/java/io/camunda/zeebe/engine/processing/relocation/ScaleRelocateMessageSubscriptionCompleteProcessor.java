/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.agrona.collections.MutableBoolean;

public class ScaleRelocateMessageSubscriptionCompleteProcessor
    implements TypedRecordProcessor<ScaleRecord> {
  private final StateWriter stateWriter;
  private final MessageSubscriptionState subscriptionState;

  public ScaleRelocateMessageSubscriptionCompleteProcessor(
      final Writers writers, final MessageSubscriptionState subscriptionState) {
    stateWriter = writers.state();
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(), ScaleIntent.RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETED, record.getValue());
    final var correlationKey =
        record.getValue().getMessageSubscriptionRecord().getCorrelationKeyBuffer();

    final var foundAny = new MutableBoolean();
    subscriptionState.visitSubscriptions(
        subscription -> {
          if (subscription.getRecord().getCorrelationKeyBuffer().equals(correlationKey)) {
            foundAny.set(true);
            return false;
          } else {
            return true;
          }
        });

    if (!foundAny.get()) {
      // TODO: Write command to continue with messages
    }
  }
}
