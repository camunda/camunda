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
 * <p>The same applier fires on both partitions involved in the handshake — and, in single-partition
 * deployments, fires twice on the same stream (once for the local STARTED that the request
 * processor writes immediately after activation, once for the STARTED that the reply processor
 * writes on top of the START reply command). Each side-effect below is designed to be safe under
 * every combination:
 *
 * <ul>
 *   <li><b>Dedup entry on {@code P_B}.</b> Always written; the underlying {@code putActive} is
 *       idempotent under retry and tolerates re-claim by a fresh PI after a banned holder or an
 *       expired tombstone. On {@code P_K} the write is wasted state but never read, so leaving it
 *       in place keeps the applier symmetric and avoids needing a routing-aware discriminator.
 *   <li><b>Pending-ask removal on {@code P_K}.</b> Always called; {@code remove} is a no-op when
 *       the entry is absent. On {@code P_B} there is no pending-ask, so this is also a harmless
 *       no-op.
 *   <li><b>Cross-partition businessId discriminator on {@code P_K}.</b> Marks the local
 *       correlation-key lock entry that the {@link MessageStartEventSubscriptionCorrelatedApplier}
 *       wrote when the reply processor emitted CORRELATED. Only written when the lock entry
 *       actually exists locally — this skips both the P_B side in multi-partition mode (no lock
 *       entry there) and the first of the two STARTED firings in single-partition mode (CORRELATED
 *       has not run yet). The lock-entry presence is the natural discriminator and avoids needing
 *       routing info inside the applier. The write itself is {@code upsert} and therefore
 *       idempotent under retry.
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
    dedupState.putActive(
        value.getProcessDefinitionKey(), value.getMessageKey(), value.getProcessInstanceKey());

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
