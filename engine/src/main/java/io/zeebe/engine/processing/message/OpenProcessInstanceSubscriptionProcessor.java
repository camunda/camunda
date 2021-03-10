/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import io.zeebe.engine.state.mutable.MutableWorkflowInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public final class OpenWorkflowInstanceSubscriptionProcessor
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to open workflow instance subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  public static final String NOT_OPENING_MSG =
      "Expected to open workflow instance subscription with element key '%d' and message name '%s', "
          + "but it is already %s";
  private final MutableWorkflowInstanceSubscriptionState subscriptionState;

  public OpenWorkflowInstanceSubscriptionProcessor(
      final MutableWorkflowInstanceSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final WorkflowInstanceSubscriptionRecord subscriptionRecord = record.getValue();
    final WorkflowInstanceSubscription subscription =
        subscriptionState.getSubscription(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer());

    if (subscription != null && subscription.isOpening()) {

      subscriptionState.updateToOpenedState(
          subscription, subscription.getSubscriptionPartitionId());

      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.OPENED, subscriptionRecord);

    } else {
      final String messageName =
          BufferUtil.bufferAsString(subscriptionRecord.getMessageNameBuffer());

      if (subscription == null) {
        streamWriter.appendRejection(
            record,
            RejectionType.NOT_FOUND,
            String.format(
                NO_SUBSCRIPTION_FOUND_MESSAGE,
                subscriptionRecord.getElementInstanceKey(),
                messageName));
      } else {
        final String state = subscription.isClosing() ? "closing" : "opened";
        streamWriter.appendRejection(
            record,
            RejectionType.INVALID_STATE,
            String.format(
                NOT_OPENING_MSG, subscriptionRecord.getElementInstanceKey(), messageName, state));
      }
    }
  }
}
