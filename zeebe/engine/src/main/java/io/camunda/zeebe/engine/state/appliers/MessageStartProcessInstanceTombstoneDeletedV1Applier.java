/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Removes the cross-partition message-start dedup entry once its post-completion tombstone window
 * has passed. Emitted by the dedup tombstone sweep on {@code P_B}; deletes both forward and reverse
 * column families for the given {@code (processDefinitionKey, messageKey)} pair.
 */
final class MessageStartProcessInstanceTombstoneDeletedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceDedupState dedupState;

  MessageStartProcessInstanceTombstoneDeletedV1Applier(
      final MutableMessageStartProcessInstanceDedupState dedupState) {
    this.dedupState = dedupState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    dedupState.delete(value.getProcessDefinitionKey(), value.getMessageKey());
  }
}
