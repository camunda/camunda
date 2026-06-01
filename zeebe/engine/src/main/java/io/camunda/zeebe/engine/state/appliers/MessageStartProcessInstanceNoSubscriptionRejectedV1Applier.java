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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Removes the pending cross-partition ask entry on {@code P_K} when a {@link
 * MessageStartProcessInstanceRequestIntent#NO_SUBSCRIPTION_REJECTED} reply is applied. The message
 * stays buffered (preserving the same semantics as when a local start-event subscription is
 * missing), but the pending-ask bookkeeping is cleared so commit 7's scheduler does not retry-storm
 * after a final rejection.
 *
 * <p>This applier is V1-only because the {@code NO_SUBSCRIPTION_REJECTED} intent has no production
 * stream history (it is introduced together with this feature).
 */
final class MessageStartProcessInstanceNoSubscriptionRejectedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceAskState askState;

  MessageStartProcessInstanceNoSubscriptionRejectedV1Applier(
      final MutableMessageStartProcessInstanceAskState askState) {
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    askState.remove(value.getMessageKey(), value.getProcessDefinitionKey());
  }
}
