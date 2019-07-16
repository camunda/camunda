/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import java.util.Collections;

public class OpenMessageStartEventSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartEventSubscriptionRecord> {

  private final MessageStartEventSubscriptionState subscriptionState;
  private final WorkflowState workflowState;

  public OpenMessageStartEventSubscriptionProcessor(
      MessageStartEventSubscriptionState subscriptionState, WorkflowState workflowState) {
    this.subscriptionState = subscriptionState;
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageStartEventSubscriptionRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final MessageStartEventSubscriptionRecord subscription = record.getValue();
    subscriptionState.put(subscription);

    workflowState
        .getEventScopeInstanceState()
        .createIfNotExists(subscription.getWorkflowKey(), Collections.emptyList());

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageStartEventSubscriptionIntent.OPENED, subscription);
  }
}
