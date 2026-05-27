/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceDedupEntry;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;

/**
 * Handles the cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST} command on
 * {@code P_B = hash(businessId)}.
 *
 * <p>On every request, the processor first consults the per-(processDefinitionKey, messageKey)
 * dedup state. A hit short-circuits the live-state evaluation and re-replies {@link
 * MessageStartProcessInstanceRequestIntent#STARTED} with the cached process-instance key, so a
 * retry from {@code P_K}'s pending-ask state never produces a second PI. A hit is treated as valid
 * when its {@code deletionDeadline} has not yet passed <em>and</em> the cached PI is not banned;
 * otherwise (no entry, expired entry, or banned holder) the request falls through to live-state
 * evaluation. The dedup entry's {@code deletionDeadline} is taken directly from the request's
 * {@code messageDeadline} (= {@code publishTime + ttl} on {@code P_K}), so the dedup row on {@code
 * P_B} and the buffered message on {@code P_K} share the same lifetime without any engine-internal
 * time coupling; the entry exists to bound {@code P_K}'s retry window — the sole correctness
 * contract that prevents duplicate creates is {@code retryDeadline <= messageDeadline}, enforced on
 * {@code P_K} by the {@link io.camunda.zeebe.engine.state.appliers.MessageExpiredApplier}-driven
 * pending-ask cleanup.
 *
 * <p>On a cache miss the three live-state outcomes are the same as before:
 *
 * <ul>
 *   <li>the start-event subscription is not locally present (deployment-distribution race) → reply
 *       {@link MessageStartProcessInstanceRequestIntent#REJECT_NO_SUBSCRIPTION}; the publisher's
 *       message stays buffered on {@code P_K};
 *   <li>an active root PI on this partition already holds the {@code businessId} for this process
 *       definition → reply {@link MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS}; the
 *       publisher's message stays buffered on {@code P_K};
 *   <li>otherwise → activate the new PI locally, write a local {@link
 *       MessageStartProcessInstanceRequestIntent#STARTED} follow-up event (whose applier records
 *       the dedup entry), and dispatch the {@link MessageStartProcessInstanceRequestIntent#START}
 *       reply back to {@code P_K}.
 * </ul>
 *
 * <p>Rejection outcomes are deliberately not deduplicated: each retry re-evaluates live state on
 * {@code P_B}, so a holder PI that has since completed (or a deployment that has since arrived) can
 * flip a previously-rejected ask into a fresh success. Both rejection outcomes are engine-level
 * idempotent, so re-evaluation is always safe.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final MessageStartProcessInstanceDedupState dedupState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final EventHandle eventHandle;
  private final KeyGenerator keyGenerator;
  private final InstantSource clock;
  private final MessageStartProcessInstanceRequestRecord startedEventBuffer =
      new MessageStartProcessInstanceRequestRecord();

  public MessageStartProcessInstanceRequestProcessor(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final MessageStartProcessInstanceDedupState dedupState,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final InstantSource clock,
      final Writers writers) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.dedupState = dedupState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.clock = clock;
    eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<MessageStartProcessInstanceRequestRecord> record) {
    final var request = record.getValue();

    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.REQUESTED, request);

    final var cachedPiKey = lookupValidDedupHit(request);
    if (cachedPiKey != null) {
      commandSender.sendStartProcessInstanceStarted(request, cachedPiKey);
      return;
    }

    if (!localSubscriptionExists(request)) {
      commandSender.sendStartProcessInstanceNoSubscriptionRejected(request);
      return;
    }

    if (isBusinessIdAlreadyHeld(request)) {
      commandSender.sendStartProcessInstanceUniquenessRejected(request);
      return;
    }

    final long processInstanceKey = keyGenerator.nextKey();
    // Activate the new PI directly. We do NOT use EventHandle#triggerMessageStartEvent here
    // because that path also writes a MessageStartEventSubscription:CORRELATED event, whose
    // applier dereferences the buffered message in local MessageState — but on P_B the message
    // has never been published locally. The CORRELATED event against the originating
    // subscription is written on P_K when it applies the STARTED reply.
    eventHandle.activateProcessInstanceForStartEvent(
        request.getProcessDefinitionKey(),
        processInstanceKey,
        request.getStartEventIdBuffer(),
        request.getVariablesBuffer(),
        request.getTenantId(),
        request.getBusinessIdBuffer());

    // Write a local STARTED follow-up event so the dedup applier records
    // (processDefinitionKey, messageKey) → processInstanceKey. The cross-partition reply
    // command triggers a second STARTED event on P_K whose applier clears the pending-ask
    // bookkeeping; this local event is what populates the dedup state on P_B.
    startedEventBuffer.wrap(request);
    startedEventBuffer.setProcessInstanceKey(processInstanceKey);
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageStartProcessInstanceRequestIntent.STARTED, startedEventBuffer);

    commandSender.sendStartProcessInstanceStarted(request, processInstanceKey);
  }

  /**
   * Returns the cached process-instance key when a valid dedup entry exists for the request, or
   * {@code null} when the request must fall through to live-state evaluation. A hit is valid when
   * its {@code deletionDeadline} has not yet passed and the cached PI is not currently banned;
   * banned holders are filtered out so a retry can produce a fresh PI, mirroring the lookup-time
   * banned filter on the live-state uniqueness check.
   */
  private Long lookupValidDedupHit(final MessageStartProcessInstanceRequestRecord request) {
    final MessageStartProcessInstanceDedupEntry entry =
        dedupState.get(request.getProcessDefinitionKey(), request.getMessageKey());
    if (entry == null) {
      return null;
    }
    if (entry.getDeletionDeadline() <= clock.millis()) {
      return null;
    }
    final long cachedPiKey = entry.getProcessInstanceKey();
    return bannedInstanceState.isProcessInstanceBanned(cachedPiKey) ? null : cachedPiKey;
  }

  /**
   * Returns {@code true} when this partition has the message-start-event subscription for the
   * requested {@code (tenantId, messageName, processDefinitionKey)} triple. A missing subscription
   * is typically a deployment-distribution race: {@code P_K} already sees it locally, but it has
   * not reached {@code P_B} yet. The O(1) keyed lookup is sufficient because the subscription
   * fields {@code P_B} needs for activation (bpmnProcessId, startEventId, tenantId,
   * processDefinitionKey) are carried verbatim on the request, captured from {@code P_K}'s copy of
   * the same deployment.
   */
  private boolean localSubscriptionExists(final MessageStartProcessInstanceRequestRecord request) {
    return startEventSubscriptionState.exists(
        new MessageStartEventSubscriptionRecord()
            .setTenantId(request.getTenantId())
            .setMessageName(request.getMessageNameBuffer())
            .setProcessDefinitionKey(request.getProcessDefinitionKey()));
  }

  /**
   * Reuses the existing local-arm predicate so the cross-partition check on {@code P_B} and the
   * local-arm check on a same-partition publish stay in lockstep — including the {@link
   * BannedInstanceState#isProcessInstanceBanned banned-PI} filter, without which a banned holder
   * would permanently block legitimate starts.
   */
  private boolean isBusinessIdAlreadyHeld(final MessageStartProcessInstanceRequestRecord request) {
    final var businessId = request.getBusinessIdBuffer();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        request.getBusinessId(),
        request.getBpmnProcessId(),
        request.getTenantId(),
        bannedInstanceState::isProcessInstanceBanned);
  }
}
