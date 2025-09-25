/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;

class MessageCorrelationCorrelatedApplier
    implements TypedEventApplier<MessageCorrelationIntent, MessageCorrelationRecord> {

  private final MutableMessageCorrelationState messageCorrelationState;

  public MessageCorrelationCorrelatedApplier(final MutableProcessingState state) {
    messageCorrelationState = state.getMessageCorrelationState();
  }

  @Override
  public void applyState(final long key, final MessageCorrelationRecord value) {
    messageCorrelationState.removeMessageCorrelation(value.getMessageKey());
  }
}
