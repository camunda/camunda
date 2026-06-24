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
 * Records a {@link MessageStartProcessInstanceRequestIntent#UNIQUENESS_REJECTED} reply on {@code
 * P_K} by backing the pending cross-partition ask off rather than dropping it. The message stays
 * buffered and the ask is kept with an incremented rejection count, so the scheduler re-sends it
 * under exponential back-off: when the holder instance that currently owns the {@code businessId}
 * completes (or is banned), a later retry flips to {@code STARTED}. The ask is finally cleared only
 * by a success or by the buffered message expiring at its TTL.
 *
 * <p>This applier is V1-only because the {@code UNIQUENESS_REJECTED} intent has no production
 * stream history (it is introduced together with this feature).
 */
final class MessageStartProcessInstanceUniquenessRejectedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceAskState askState;

  MessageStartProcessInstanceUniquenessRejectedV1Applier(
      final MutableMessageStartProcessInstanceAskState askState) {
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    askState.backOff(value.getMessageKey(), value.getProcessDefinitionKey());
  }
}
