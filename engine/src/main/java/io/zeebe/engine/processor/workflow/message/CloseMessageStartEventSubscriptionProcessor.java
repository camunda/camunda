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
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;

public class CloseMessageStartEventSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartEventSubscriptionRecord> {

  private final MessageStartEventSubscriptionState subscriptionState;

  public CloseMessageStartEventSubscriptionProcessor(
      MessageStartEventSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      TypedRecord<MessageStartEventSubscriptionRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final MessageStartEventSubscriptionRecord subscriptionRecord = record.getValue();
    final long workflowKey = subscriptionRecord.getWorkflowKey();

    subscriptionState.removeSubscriptionsOfWorkflow(workflowKey);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageStartEventSubscriptionIntent.CLOSED, subscriptionRecord);
  }
}
