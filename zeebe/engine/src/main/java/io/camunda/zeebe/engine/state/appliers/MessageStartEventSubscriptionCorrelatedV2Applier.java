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

/**
 * Version 2 of {@link MessageStartEventSubscriptionCorrelatedApplier} (ADR 0014, D7 parallel-CF
 * prototype). When the correlated message-start carries a run scope, the start-uniqueness lock is
 * written to the run-scoped CF instead of the unscoped one, so two runs that share a correlation
 * key each get their own lock entry. With no run scope the behaviour is identical to v1, which
 * stays registered for replay of events written before the upgrade.
 */
public final class MessageStartEventSubscriptionCorrelatedV2Applier
    implements TypedEventApplier<
        MessageStartEventSubscriptionIntent, MessageStartEventSubscriptionRecord> {

  private final MutableMessageState messageState;

  public MessageStartEventSubscriptionCorrelatedV2Applier(final MutableMessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageStartEventSubscriptionRecord value) {
    // avoid correlating this message to one instance of this process again
    messageState.putMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());

    final DirectBuffer correlationKey = value.getCorrelationKeyBuffer();
    if (correlationKey.capacity() > 0) {
      // lock the process for this correlation key so other messages with the same key are not
      // correlated to this process until the created instance ends. A run-scoped start locks only
      // within its run; an unscoped start locks globally, exactly as before.
      final DirectBuffer runScope = value.getRunScopeBuffer();
      messageState.putActiveProcessInstance(
          value.getBpmnProcessIdBuffer(), correlationKey, runScope);
      messageState.putProcessInstanceCorrelationKey(value.getProcessInstanceKey(), correlationKey);
      if (runScope.capacity() > 0) {
        messageState.putProcessInstanceRunScope(value.getProcessInstanceKey(), runScope);
      }
    }
  }
}
