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
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import java.util.Collections;

public final class OpenMessageStartEventSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartEventSubscriptionRecord> {

  private final MessageStartEventSubscriptionState subscriptionState;
  private final WorkflowState workflowState;

  public OpenMessageStartEventSubscriptionProcessor(
      final MessageStartEventSubscriptionState subscriptionState,
      final WorkflowState workflowState) {
    this.subscriptionState = subscriptionState;
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageStartEventSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final MessageStartEventSubscriptionRecord subscription = record.getValue();
    subscriptionState.put(subscription);

    workflowState
        .getEventScopeInstanceState()
        .createIfNotExists(subscription.getWorkflowKey(), Collections.emptyList());

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageStartEventSubscriptionIntent.OPENED, subscription);
  }
}
