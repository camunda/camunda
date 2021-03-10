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
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessInstanceSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.ProcessInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public final class CloseProcessInstanceSubscription
    implements TypedRecordProcessor<ProcessInstanceSubscriptionRecord> {
  public static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to close process instance subscription for element with key '%d' and message name '%s', "
          + "but no such subscription was found";

  private final MutableProcessInstanceSubscriptionState subscriptionState;

  public CloseProcessInstanceSubscription(
      final MutableProcessInstanceSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      final TypedRecord<ProcessInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final ProcessInstanceSubscriptionRecord subscription = record.getValue();

    final boolean removed =
        subscriptionState.remove(
            subscription.getElementInstanceKey(), subscription.getMessageNameBuffer());
    if (removed) {
      streamWriter.appendFollowUpEvent(
          record.getKey(), ProcessInstanceSubscriptionIntent.CLOSED, subscription);

    } else {
      streamWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              NO_SUBSCRIPTION_FOUND_MESSAGE,
              subscription.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscription.getMessageNameBuffer())));
    }
  }
}
