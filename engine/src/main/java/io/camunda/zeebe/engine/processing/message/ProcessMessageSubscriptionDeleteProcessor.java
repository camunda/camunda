/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.api.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ProcessMessageSubscriptionDeleteProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to delete process message subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ProcessMessageSubscriptionState subscriptionState;

  public ProcessMessageSubscriptionDeleteProcessor(
      final ProcessMessageSubscriptionState subscriptionState, final Writers writers) {
    this.subscriptionState = subscriptionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessMessageSubscriptionRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final ProcessMessageSubscriptionRecord subscriptionRecord = command.getValue();

    final var subscription =
        subscriptionState.getSubscription(
            command.getValue().getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer());

    if (subscription == null) {
      rejectCommand(command);
      return;
    }

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.DELETED, subscription.getRecord());
  }

  private void rejectCommand(final TypedRecord<ProcessMessageSubscriptionRecord> command) {
    final var reason =
        String.format(
            NO_SUBSCRIPTION_FOUND_MESSAGE,
            command.getValue().getElementInstanceKey(),
            BufferUtil.bufferAsString(command.getValue().getMessageNameBuffer()));

    rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
  }
}
