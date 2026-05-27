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
 * Records that {@code P_B} has successfully started a process instance in response to a
 * cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST}, and on {@code P_K}
 * tears down the matching pending-ask entry so the retry scheduler stops resending.
 *
 * <p>On {@code P_B}, writes the {@code (processDefinitionKey, messageKey) → (processInstanceKey,
 * deletionDeadline)} entry into the dedup column family. The {@code deletionDeadline} is taken
 * directly from the request record's {@code messageDeadline} — the deadline of the originating
 * buffered message on {@code P_K} ({@code publishTime + ttl}) — so the dedup row on {@code P_B}
 * and the buffered message on {@code P_K} share the same lifetime without any engine-internal
 * time coupling. The deadline is never updated; the entry exists to bound {@code P_K}'s retry
 * window, not to track the holder PI's lifecycle. Combined with the lookup-time banned-PI filter
 * and the scheduled sweeper, this defends {@code P_B} against retries from {@code P_K}'s
 * pending-ask state. {@code put} is upsert: a fresh {@code STARTED} after a previous holder was
 * banned (or after the previous deadline passed but the sweep had not yet run) replaces the prior
 * entry with the new {@code processInstanceKey} and the deadline carried by the retry's record.
 *
 * <p>On {@code P_K}, removes the pending-ask entry for {@code (messageKey, processDefinitionKey)}.
 * The call is unconditional; {@code remove} is a no-op when the entry is absent, which is the
 * normal case on {@code P_B} where no pending-ask was ever written. The dedup {@code put} on
 * {@code P_K} is wasted state but never read, so leaving it in place keeps the applier symmetric
 * and avoids routing logic inside the applier.
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
    dedupState.put(
        value.getProcessDefinitionKey(),
        value.getMessageKey(),
        value.getProcessInstanceKey(),
        value.getMessageDeadline());

    // On P_K: remove the pending ask so the retry scheduler stops resending. Safe to call
    // unconditionally; remove() is a no-op when no entry exists.
    askState.remove(value.getMessageKey(), value.getProcessDefinitionKey());
  }
}

