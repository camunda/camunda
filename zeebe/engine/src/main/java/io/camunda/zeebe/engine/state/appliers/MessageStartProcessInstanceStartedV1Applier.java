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
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Records that a process instance was successfully started in response to a cross-partition {@link
 * MessageStartProcessInstanceRequestIntent#REQUEST}.
 *
 * <p>This applier fires on <b>both</b> partitions:
 *
 * <ul>
 *   <li><b>On {@code P_B}:</b> writes the {@code (processDefinitionKey, messageKey) →
 *       processInstanceKey} dedup entry that lets retries from {@code P_K} be re-replied without a
 *       second activation. The forward dedup column family stores the resulting {@code
 *       processInstanceKey}; the reverse column family maps {@code processInstanceKey} back to that
 *       pair, so the cleanup hook on PI completion can transition the entry to {@code TOMBSTONE} in
 *       O(1) without scanning.
 *   <li><b>On {@code P_K}:</b> removes the pending cross-partition ask entry so commit 7's
 *       scheduler does not retry after a successful reply. The dedup state is null on {@code P_K}
 *       (it only exists on {@code P_B}), so that write is skipped.
 * </ul>
 */
final class MessageStartProcessInstanceStartedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceDedupState dedupState;
  private final MutableMessageStartProcessInstanceAskState askState;

  MessageStartProcessInstanceStartedV1Applier(
      final MutableMessageStartProcessInstanceDedupState dedupState,
      final MutableMessageStartProcessInstanceAskState askState) {
    this.dedupState = dedupState;
    this.askState = askState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    // On P_B: record the dedup entry so retries from P_K get the same reply
    dedupState.putActive(
        value.getProcessDefinitionKey(), value.getMessageKey(), value.getProcessInstanceKey());

    // On P_K: remove the pending ask so the scheduler stops retrying
    // This is safe to call unconditionally; remove() is a no-op if the entry doesn't exist
    askState.remove(value.getMessageKey(), value.getProcessDefinitionKey());
  }
}
