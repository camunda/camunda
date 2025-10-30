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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;

public final class MessagePublishedApplier
    implements TypedEventApplier<MessageIntent, MessageRecord> {

  private final MutableMessageState messageState;

  public MessagePublishedApplier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageRecord value) {
    messageState.put(key, value);
  }
}
