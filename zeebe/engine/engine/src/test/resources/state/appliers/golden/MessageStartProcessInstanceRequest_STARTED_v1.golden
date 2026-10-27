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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;

/**
 * Records the state effects of a successful cross-partition message-start {@link
 * MessageStartProcessInstanceRequestIntent#STARTED} reply.
 *
 * <p>The handshake — and therefore this applier — is engaged only when the holder's {@code
 * businessId} hashes to a different partition than the message's correlation key ({@code P_B !=
 * P_K}). Single-partition deployments, and the case where {@code businessId} and correlation key
 * happen to hash to the same partition, take the local message-start path instead and never reach
 * this applier. When engaged, the applier fires once on each side: on {@code P_B} for the local
 * {@code STARTED} the request processor writes right after activation, and on {@code P_K} for the
 * {@code STARTED} the start-reply processor writes after {@code CORRELATED}. Each side-effect below
 * is designed to be safe on both sides:
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
 *   <li><b>Holder-origin entry on {@code P_B}.</b> Records, keyed by the holder instance key, the
 *       lock coordinates ({@code bpmnProcessId}, {@code correlationKey}, {@code tenantId}) plus the
 *       {@code messageKey} whose partition bits address {@code P_K}, so that when the holder later
 *       completes or terminates on {@code P_B} it can push a {@code RELEASE} to {@code P_K} without
 *       re-deriving those coordinates from the completing element's context (the completing context
 *       is unreliable under migration — the holder may since run a different process id).
 *       Discriminated to {@code P_B} by the holder instance key's partition bits: the PI was
 *       created on {@code P_B}, so {@code decodePartitionId(processInstanceKey) == partitionId}
 *       there, whereas on {@code P_K} the same reply carries a PI key that decodes to {@code P_B}
 *       and is skipped — the mirror of the discriminator used by the lock write below. Written only
 *       when the holder carries a correlation key: without one there is no correlation-key lock on
 *       {@code P_K} to release. The write is {@code upsert} and therefore idempotent under a
 *       re-applied {@code STARTED} reply.
 *   <li><b>Cross-partition holder-instance discriminator on {@code P_K}.</b> Marks the local
 *       correlation-key lock entry that the {@link MessageStartEventSubscriptionCorrelatedApplier}
 *       wrote when the reply processor emitted {@code CORRELATED}, recording the holder instance's
 *       {@code (processInstanceKey, tenantId)}. Only written when the lock entry exists locally: on
 *       {@code P_B} the lock entry never exists ({@code CORRELATED} runs on {@code P_K}), so this
 *       is skipped there; on {@code P_K} the start-reply processor writes {@code CORRELATED} before
 *       {@code STARTED}, so the entry is present. The lock-entry presence is the natural
 *       discriminator and avoids needing routing info inside the applier. The write itself is
 *       {@code upsert} and therefore idempotent under retry.
 * </ul>
 *
 * <p>The lock-entry presence + holder-instance discriminator together encode the lock-release
 * contract documented at {@link
 * io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior}: every active root PI with a
 * {@code businessId} lives on {@code P_B = hash(businessId)}, and {@code P_K} keeps a local
 * correlation-key lock so further triggers with the same correlation key are buffered regardless of
 * their {@code businessId}. The recorded holder instance is what {@code P_K}'s reconciliation poll
 * queries {@code P_B} for, as a backstop deciding when that lock can be released if the holder's
 * completion push from {@code P_B} was lost.
 */
final class MessageStartProcessInstanceStartedV1Applier
    implements TypedEventApplier<
        MessageStartProcessInstanceRequestIntent, MessageStartProcessInstanceRequestRecord> {

  private final MutableMessageStartProcessInstanceDedupState dedupState;
  private final MutableMessageStartProcessInstanceAskState askState;
  private final MutableMessageState messageState;
  private final int partitionId;

  MessageStartProcessInstanceStartedV1Applier(
      final MutableMessageStartProcessInstanceDedupState dedupState,
      final MutableMessageStartProcessInstanceAskState askState,
      final MutableMessageState messageState,
      final int partitionId) {
    this.dedupState = dedupState;
    this.askState = askState;
    this.messageState = messageState;
    this.partitionId = partitionId;
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

    // On P_B: record the holder-origin entry so the holder's completion can later push a RELEASE to
    // P_K. Discriminated to P_B by the holder key's partition bits, and skipped without a
    // correlation key (no lock to release). See the class javadoc for the full rationale.
    if (correlationKey.capacity() > 0
        && Protocol.decodePartitionId(value.getProcessInstanceKey()) == partitionId) {
      messageState.putCrossPartitionStartHolderOrigin(
          value.getProcessInstanceKey(),
          value.getBpmnProcessIdBuffer(),
          correlationKey,
          value.getTenantId(),
          value.getMessageKey());
    }

    if (correlationKey.capacity() > 0
        && businessId.capacity() > 0
        && messageState.existActiveProcessInstance(
            value.getTenantId(), value.getBpmnProcessIdBuffer(), correlationKey)) {
      messageState.putCrossPartitionStartLock(
          value.getBpmnProcessIdBuffer(),
          correlationKey,
          value.getProcessInstanceKey(),
          value.getTenantId());
    }
  }
}
