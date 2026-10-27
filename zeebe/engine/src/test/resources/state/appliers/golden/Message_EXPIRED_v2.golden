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

/**
 * Applies {@link MessageIntent#EXPIRED} (V2): removes the buffered message and additionally clears
 * any cross-partition message-start pending-ask whose buffered message just expired.
 *
 * <p>When a message that triggered a cross-partition start-process-instance ask expires on {@code
 * P_K}, the dedup row on {@code P_B} carries the same deadline as the message, so once the message
 * is gone the dedup row is also already expired and will be swept. Without clearing the
 * pending-ask, a later retry from the scheduler would miss the cached dedup row and could create a
 * duplicate process instance.
 *
 * <p>This pending-ask cleanup is feature behaviour added after {@code MessageIntent.EXPIRED}
 * already existed on production streams. It therefore lives in a new applier version registered
 * alongside the original {@link MessageExpiredApplier} (V1): pre-feature events keep replaying
 * through V1 unchanged, while new events are written at the latest version and handled here.
 */
public final class MessageExpiredV2Applier
    implements TypedEventApplier<MessageIntent, MessageRecord> {

  private final MutableMessageState messageState;
  private final MutableMessageStartProcessInstanceAskState askState;

  public MessageExpiredV2Applier(
      final MutableMessageState messageState,
      final MutableMessageStartProcessInstanceAskState askState) {
    this.messageState = messageState;
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageRecord value) {
    messageState.remove(key);
    askState.removeAllByMessageKey(key);
  }
}
