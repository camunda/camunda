/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import org.agrona.DirectBuffer;

public final class MessageStartEventSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        MessageStartEventSubscriptionIntent, MessageStartEventSubscriptionRecord> {

  private final MutableMessageState messageState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public MessageStartEventSubscriptionCorrelatedApplier(
      final MutableMessageState messageState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.messageState = messageState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void applyState(final long key, final MessageStartEventSubscriptionRecord value) {
    // avoid correlating this message to one instance of this process again
    messageState.putMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());

    final DirectBuffer correlationKey = value.getCorrelationKeyBuffer();
    if (correlationKey.capacity() > 0) {
      // lock the process for this correlation key
      // - other messages with same correlation key are not correlated to this process
      // until the created instance is ended
      messageState.putActiveProcessInstance(value.getBpmnProcessIdBuffer(), correlationKey);
      messageState.putProcessInstanceCorrelationKey(value.getProcessInstanceKey(), correlationKey);
    }

    // write the event trigger for the start event
    eventScopeInstanceState.triggerEvent(
        value.getProcessDefinitionKey(),
        value.getMessageKey(),
        value.getStartEventIdBuffer(),
        value.getVariablesBuffer());
  }
}
