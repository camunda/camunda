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

/**
 * Applies {@link MessageIntent#EXPIRED} (V1): removes the buffered message.
 *
 * <p>This is the original, pre-feature behaviour and is retained so that {@code
 * MessageIntent.EXPIRED} events written before the cross-partition message-start handshake existed
 * continue to replay deterministically. New events are written at the latest version and handled by
 * {@link MessageExpiredV2Applier}, which additionally clears the cross-partition pending-ask state.
 */
public final class MessageExpiredApplier
    implements TypedEventApplier<MessageIntent, MessageRecord> {

  private final MutableMessageState messageState;

  public MessageExpiredApplier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageRecord value) {
    messageState.remove(key);
  }
}
