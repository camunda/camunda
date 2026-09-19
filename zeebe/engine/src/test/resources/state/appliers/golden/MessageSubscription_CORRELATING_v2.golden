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
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

/**
 * V2 of the CORRELATING applier. In addition to V1's behavior, records which subscription claimed
 * the correlation lock for this exact message, so a later stale-generation REJECTED reject can tell
 * whether it still owns that lock from the lock's own recorded owner instead of from this
 * subscription's row, which only ever reflects its <em>latest</em> message.
 *
 * <p>See {@link MessageSubscriptionRejectedV2Applier} and <a
 * href="https://github.com/camunda/camunda/issues/61099">#61099</a>.
 */
public final class MessageSubscriptionCorrelatingV2Applier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageSubscriptionState messageSubscriptionState;
  private final MutableMessageState messageState;

  public MessageSubscriptionCorrelatingV2Applier(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final MutableMessageState messageState) {
    this.messageSubscriptionState = messageSubscriptionState;
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    messageSubscriptionState.updateToCorrelatingState(value);

    // avoid correlating this message to one instance of this process again; also records the
    // owning subscription so a later stale-generation reject can tell this claim apart from one
    // this same subscription has since moved on from
    messageState.putMessageCorrelation(
        value.getMessageKey(), value.getBpmnProcessIdBuffer(), value.getSubscriptionKey());
  }
}
