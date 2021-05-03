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
import io.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.zeebe.engine.state.message.ProcessMessageSubscription;
import io.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public final class ProcessMessageSubscriptionCreateProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String NOT_OPENING_MSG =
      "Expected to create process message subscription with element key '%d' and message name '%s', "
          + "but it is already %s";

  private final ProcessMessageSubscriptionState subscriptionState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public ProcessMessageSubscriptionCreateProcessor(
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
    final ProcessMessageSubscription subscription =
        subscriptionState.getSubscription(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer());

    if (subscription != null && subscription.isOpening()) {
      stateWriter.appendFollowUpEvent(
          subscription.getKey(),
          ProcessMessageSubscriptionIntent.CREATED,
          subscription.getRecord());

    } else {
      rejectCommand(command, subscription);
    }
  }

  private void rejectCommand(
      final TypedRecord<ProcessMessageSubscriptionRecord> command,
      final ProcessMessageSubscription subscription) {
    final var record = command.getValue();
    final var elementInstanceKey = record.getElementInstanceKey();
    final String messageName = BufferUtil.bufferAsString(record.getMessageNameBuffer());

    if (subscription == null) {
      final var reason =
          String.format(NO_SUBSCRIPTION_FOUND_MESSAGE, elementInstanceKey, messageName);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);

    } else {
      final String state = subscription.isClosing() ? "closing" : "opened";
      final var reason = String.format(NOT_OPENING_MSG, elementInstanceKey, messageName, state);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
    }
  }
}
