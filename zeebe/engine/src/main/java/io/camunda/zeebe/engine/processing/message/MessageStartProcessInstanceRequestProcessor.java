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
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Handles the cross-partition {@link MessageStartProcessInstanceRequestIntent#REQUEST} command on
 * {@code P_B = hash(businessId)}. Three outcomes are possible:
 *
 * <ul>
 *   <li>the start-event subscription is not locally present (deployment-distribution race) → reply
 *       {@link MessageStartProcessInstanceRequestIntent#REJECT_NO_SUBSCRIPTION} to {@code P_K}; the
 *       publisher's message stays buffered there;
 *   <li>an active root PI on this partition already holds the {@code businessId} for this process
 *       definition → reply {@link MessageStartProcessInstanceRequestIntent#REJECT_UNIQUENESS}; the
 *       publisher's message stays buffered there;
 *   <li>otherwise → trigger the message-start event locally via {@link EventHandle}, then reply
 *       {@link MessageStartProcessInstanceRequestIntent#START} carrying the new process-instance
 *       key.
 * </ul>
 *
 * <p>Idempotency against retries from {@code P_K}'s pending-ask state is handled by a dedup state
 * introduced in a later commit. This processor only writes a {@link
 * MessageStartProcessInstanceRequestIntent#REQUESTED} acknowledgement event for the request and
 * dispatches the reply; it does not yet consult or maintain dedup entries.
 */
@ExcludeAuthorizationCheck
public final class MessageStartProcessInstanceRequestProcessor
    implements TypedRecordProcessor<MessageStartProcessInstanceRequestRecord> {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final EventHandle eventHandle;
  private final KeyGenerator keyGenerator;

  public MessageStartProcessInstanceRequestProcessor(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
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
    // subscription is written on P_K when it applies the STARTED reply (lands in a later commit).
    eventHandle.activateProcessInstanceForStartEvent(
        request.getProcessDefinitionKey(),
        processInstanceKey,
        request.getStartEventIdBuffer(),
        request.getVariablesBuffer(),
        request.getTenantId(),
        request.getBusinessIdBuffer());

    commandSender.sendStartProcessInstanceStarted(request, processInstanceKey);
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
