/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.ProcessInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessInstanceSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public final class ProcessInstanceSubscriptionDeleteProcessor
    implements TypedRecordProcessor<ProcessInstanceSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to delete process instance subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found.";

  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ProcessInstanceSubscriptionState subscriptionState;

  public ProcessInstanceSubscriptionDeleteProcessor(
      final ProcessInstanceSubscriptionState subscriptionState, final Writers writers) {
    this.subscriptionState = subscriptionState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceSubscriptionRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final ProcessInstanceSubscriptionRecord subscription = command.getValue();

    final var exists =
        subscriptionState.existSubscriptionForElementInstance(
            subscription.getElementInstanceKey(), subscription.getMessageNameBuffer());

    if (exists) {
      stateWriter.appendFollowUpEvent(
          command.getKey(), ProcessInstanceSubscriptionIntent.DELETED, subscription);

    } else {
      rejectCommand(command);
    }
  }

  private void rejectCommand(final TypedRecord<ProcessInstanceSubscriptionRecord> command) {
    final var reason =
        String.format(
            NO_SUBSCRIPTION_FOUND_MESSAGE,
            command.getValue().getElementInstanceKey(),
            BufferUtil.bufferAsString(command.getValue().getMessageNameBuffer()));

    rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
  }
}
