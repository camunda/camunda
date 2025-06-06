/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import org.agrona.DirectBuffer;

public final class MessageStartEventSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        MessageStartEventSubscriptionIntent, MessageStartEventSubscriptionRecord> {

  private final MutableMessageState messageState;

  public MessageStartEventSubscriptionCorrelatedApplier(final MutableMessageState messageState) {
    this.messageState = messageState;
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
  }
}
