/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;

public final class MessageExpiredApplier
    implements TypedEventApplier<MessageIntent, MessageRecord> {

  private final MutableMessageState messageState;
  private final MutableMessageStartProcessInstanceAskState askState;

  public MessageExpiredApplier(
      final MutableMessageState messageState,
      final MutableMessageStartProcessInstanceAskState askState) {
    this.messageState = messageState;
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageRecord value) {
    messageState.remove(key);
    // Clear any cross-partition pending-ask whose buffered message just expired. The dedup row on
    // P_B carries the same deadline as the message, so once the message is gone, the dedup row is
    // also already expired and will be swept. Without this clear, a retry from the scheduler would
    // miss the cached dedup row and could create a duplicate process instance.
    askState.removeAllByMessageKey(key);
  }
}
