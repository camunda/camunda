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
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Records the state effects of a successful cross-partition message-start {@link
 * MessageStartProcessInstanceRequestIntent#STARTED} reply.
 *
 * <p>The same applier fires on both partitions involved in the handshake -- and, in
 * single-partition deployments, fires twice on the same stream (once for the local {@code STARTED}
 * that the request processor writes immediately after activation, once for the {@code STARTED} that
 * the reply processor writes on top of the {@code START} reply command). Each side-effect below is
 * designed to be safe under every combination:
 *
 * <ul>
 *   <li><b>Dedup upsert on {@code P_B}.</b> Writes {@code (processDefinitionKey, messageKey) ->
 *       (processInstanceKey, deletionDeadline)} into the dedup column family. The {@code
 *       deletionDeadline} is taken directly from the request record's {@code messageDeadline} — the
 *       deadline of the originating buffered message on {@code P_K} ({@code publishTime + ttl}) —
 *       so the dedup row on {@code P_B} and the buffered message on {@code P_K} share the same
 *       lifetime without any engine-internal time coupling. The entry exists to bound {@code P_K}'s
 *       retry window, not to track the holder PI's lifecycle. Combined with the lookup-time
 *       banned-PI filter and the scheduled sweeper, this defends {@code P_B} against retries from
 *       {@code P_K}'s pending-ask state. Upsert semantics let a fresh {@code STARTED} after a
 *       previous holder was banned (or after the previous deadline passed but the sweep had not yet
 *       run) replace the prior entry with a new {@code processInstanceKey} and the deadline carried
 *       by the retry's record. On {@code P_K} the put is wasted state but never read, so leaving it
 *       in place keeps the applier symmetric and avoids routing logic inside the applier.
 *   <li><b>Pending-ask removal on {@code P_K}.</b> Always called; {@code remove} is a no-op when
 *       the entry is absent. On {@code P_B} there is no pending-ask, so this is also a harmless
 *       no-op.
 *   <li><b>Cross-partition businessId discriminator on {@code P_K}.</b> Marks the local
 *       correlation-key lock entry that the {@link MessageStartEventSubscriptionCorrelatedApplier}
 *       wrote when the reply processor emitted {@code CORRELATED}. Only written when the lock entry
 *       actually exists locally -- this skips both the {@code P_B} side in multi-partition mode (no
 *       lock entry there) and the first of the two {@code STARTED} firings in single-partition mode
 *       ({@code CORRELATED} has not run yet). The lock-entry presence is the natural discriminator
 *       and avoids needing routing info inside the applier. The write itself is {@code upsert} and
 *       therefore idempotent under retry.
 * </ul>
 *
 * <p>The lock-entry presence + businessId discriminator together encode the lock-release contract
 * documented at {@link io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior}: every
 * active root PI with a {@code businessId} lives on {@code P_B = hash(businessId)}, and {@code P_K}
 * keeps a local correlation-key lock so further triggers with the same correlation key are buffered
 * regardless of their {@code businessId}.
 */
final class MessageStartProcessInstanceStartedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceDedupState dedupState;
  private final MutableMessageStartProcessInstanceAskState askState;
  private final MutableMessageState messageState;

  MessageStartProcessInstanceStartedV1Applier(
      final MutableMessageStartProcessInstanceDedupState dedupState,
      final MutableMessageStartProcessInstanceAskState askState,
      final MutableMessageState messageState) {
    this.dedupState = dedupState;
    this.askState = askState;
    this.messageState = messageState;
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

    final var correlationKey = value.getCorrelationKeyBuffer();
    final var businessId = value.getBusinessIdBuffer();
    if (correlationKey.capacity() > 0
        && businessId.capacity() > 0
        && messageState.existActiveProcessInstance(
            value.getTenantId(), value.getBpmnProcessIdBuffer(), correlationKey)) {
      messageState.putCrossPartitionStartLockBusinessId(
          value.getBpmnProcessIdBuffer(), correlationKey, businessId);
    }
  }
}
