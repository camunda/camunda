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
 * Records that {@code P_B} has successfully started a process instance in response to a
 * cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST}. The forward dedup
 * column family is keyed by {@code (processDefinitionKey, messageKey)} and stores the resulting
 * {@code processInstanceKey}; the reverse column family maps {@code processInstanceKey} back to
 * that pair, so the cleanup hook on PI completion can transition the entry to {@code TOMBSTONE} in
 * O(1) without scanning. Together with the lookup-time banned-PI filter and the tombstone window,
 * this defends {@code P_B} against retries from {@code P_K}'s pending-ask state.
 *
 * <p>This applier fires only on {@code P_B}: the matching local {@code STARTED} follow-up event is
 * written by the request processor immediately after the new PI is activated. The cross-partition
 * reply that travels back to {@code P_K} is a separate command and has no applier on {@code P_K}
 * until the bookkeeping commits land.
 */
final class MessageStartProcessInstanceStartedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceDedupState dedupState;

  MessageStartProcessInstanceStartedV1Applier(
      final MutableMessageStartProcessInstanceDedupState dedupState) {
    this.dedupState = dedupState;
  }

  @Override
  public void applyState(final long key, final MessageStartProcessInstanceRequestRecord value) {
    dedupState.putActive(
        value.getProcessDefinitionKey(), value.getMessageKey(), value.getProcessInstanceKey());
  }
}
