/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.intent.MessageIntent;

public final class MessagePublishedApplier
    implements TypedEventApplier<MessageIntent, MessageRecord> {

  private final MutableMessageState messageState;

  public MessagePublishedApplier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageRecord value) {
    // TODO (saig0): reuse the message record in the state
    final var message =
        new Message(
            key,
            value.getNameBuffer(),
            value.getCorrelationKeyBuffer(),
            value.getVariablesBuffer(),
            value.getMessageIdBuffer(),
            value.getTimeToLive(),
            value.getDeadline());

    messageState.put(message);
  }
}
