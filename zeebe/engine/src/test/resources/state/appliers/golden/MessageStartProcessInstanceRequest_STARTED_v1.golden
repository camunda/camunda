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
 * cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST}. Writes the {@code
 * (processDefinitionKey, messageKey) → (processInstanceKey, deletionDeadline)} entry into the dedup
 * column family. The {@code deletionDeadline} is taken directly from the request record's {@code
 * messageDeadline} — the deadline of the originating buffered message on {@code P_K} ({@code
 * publishTime + ttl}) — so the dedup row on {@code P_B} and the buffered message on {@code P_K}
 * share the same lifetime without any engine-internal time coupling. The deadline is never updated;
 * the entry exists to bound {@code P_K}'s retry window, not to track the holder PI's lifecycle.
 * Combined with the lookup-time banned-PI filter and the scheduled sweeper, this defends {@code
 * P_B} against retries from {@code P_K}'s pending-ask state.
 *
 * <p>This applier fires only on {@code P_B}: the matching local {@code STARTED} follow-up event is
 * written by the request processor immediately after the new PI is activated. The cross-partition
 * reply that travels back to {@code P_K} is a separate command and has no applier on {@code P_K}
 * until the bookkeeping commits land.
 *
 * <p>{@code put} is upsert: a fresh {@code STARTED} after a previous holder was banned (or after
 * the previous deadline passed but the sweep had not yet run) replaces the prior entry with the new
 * {@code processInstanceKey} and the deadline carried by the retry's record.
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
    dedupState.put(
        value.getProcessDefinitionKey(),
        value.getMessageKey(),
        value.getProcessInstanceKey(),
        value.getMessageDeadline());
  }
}
