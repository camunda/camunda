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
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;

public final class CloseMessageStartEventSubscriptionProcessor
    implements TypedRecordProcessor<MessageStartEventSubscriptionRecord> {

  private final MutableMessageStartEventSubscriptionState subscriptionState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public CloseMessageStartEventSubscriptionProcessor(
      final MutableMessageStartEventSubscriptionState subscriptionState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.subscriptionState = subscriptionState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageStartEventSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {
    final MessageStartEventSubscriptionRecord subscriptionRecord = record.getValue();
    final long processDefinitionKey = subscriptionRecord.getProcessDefinitionKey();

    subscriptionState.removeSubscriptionsOfProcess(processDefinitionKey);

    eventScopeInstanceState.deleteInstance(processDefinitionKey);

    streamWriter.appendFollowUpEvent(
        record.getKey(), MessageStartEventSubscriptionIntent.CLOSED, subscriptionRecord);
  }
}
