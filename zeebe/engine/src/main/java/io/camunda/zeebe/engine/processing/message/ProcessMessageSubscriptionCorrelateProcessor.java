/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState.PendingSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.agrona.DirectBuffer;

@ExcludeAuthorizationCheck
public final class ProcessMessageSubscriptionCorrelateProcessor
    implements TypedRecordProcessor<ProcessMessageSubscriptionRecord>,
        SuspensionAware<ProcessMessageSubscriptionRecord> {

  private static final String NO_EVENT_OCCURRED_MESSAGE =
      "Expected to correlate a process message subscription with element key '%d' and message name '%s', "
          + "but the subscription is not active anymore";
  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate process message subscription with element key '%d' and message name '%s', "
          + "but no such subscription was found";
  private static final String ALREADY_CLOSING_MESSAGE =
      "Expected to correlate process message subscription with element key '%d' and message name '%s', "
          + "but it is already closing";
  private static final String SUSPENDED_PI_MESSAGE =
      "Expected to correlate process message subscription with element key '%d' and message name '%s', "
          + "but the process instance is suspended";
  private static final String STALE_KEY_MESSAGE =
      "Expected to correlate process message subscription with element key '%d' and message name '%s', "
          + "but the command's subscription key '%d' does not match the current key '%d' — it "
          + "belongs to a superseded subscription generation";

  private final ProcessMessageSubscriptionState subscriptionState;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final SuspensionState suspensionState;

  private final EventHandle eventHandle;

  public ProcessMessageSubscriptionCorrelateProcessor(
      final ProcessMessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    this.subscriptionState = subscriptionState;
    this.transientProcessMessageSubscriptionState = transientProcessMessageSubscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    suspensionState = processingState.getSuspensionState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    eventHandle =
        new EventHandle(
            processingState.getKeyGenerator(),
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
  }

  @Override
  public void processRecord(final TypedRecord<ProcessMessageSubscriptionRecord> command) {

    final var record = command.getValue();
    final var elementInstanceKey = record.getElementInstanceKey();
    final String messageName = record.getMessageName();
    final String tenantId = record.getTenantId();
    final ProcessMessageSubscription subscription =
        subscriptionState.getSubscription(
            elementInstanceKey, record.getMessageNameBuffer(), tenantId);

    if (subscription == null) {
      rejectCommand(command, RejectionType.NOT_FOUND, NO_SUBSCRIPTION_FOUND_MESSAGE);
      return;

    } else if (subscription.isClosing()) {
      rejectCommand(command, RejectionType.INVALID_STATE, ALREADY_CLOSING_MESSAGE);
      return;

    } else if (isStaleGeneration(record, subscription)) {
      // E.g. a K1 correlate delayed across a suspend/resume cycle that re-subscribed with a new
      // key K2 — applying it would trigger the element against a subscription that no longer
      // exists on the message side.
      final var reason =
          String.format(
              STALE_KEY_MESSAGE,
              elementInstanceKey,
              messageName,
              record.getSubscriptionKey(),
              subscription.getRecord().getSubscriptionKey());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      return;

    } else if (hasAlreadyBeenCorrelated(record, subscription)) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_STATE, "Already correlated this message");
      // while we don't accept the command on this partition, we still need to acknowledge it to
      // attempt recovering from a previous acknowledgment that didn't make it to the other
      // partition.
      sendAcknowledgeCommand(record);
      return;

    } else if (suspensionState.getSuspensionState(record.getProcessInstanceKey())
        == SuspensionState.State.SUSPENDED) {
      // Race window: SUSPEND already deleted the message-side subscription, but this CORRELATE
      // was already in flight. Reject to release the message-side lock — another active
      // subscriber can then correlate, or the message returns 404. Gated on exact SUSPENDED, not
      // isSuspended(); see suspensionBehavior() below for why RESUMING must fall through instead.
      // Checked last so a stale or duplicate correlate goes through those paths instead, without
      // releasing a live replacement's correlation lock.
      rejectCommand(command, RejectionType.INVALID_STATE, SUSPENDED_PI_MESSAGE);
      return;
    }

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);
    final var canTriggerElement =
        eventHandle.canTriggerElement(
            elementInstance, subscription.getRecord().getElementIdBuffer());

    if (!canTriggerElement) {
      rejectCommand(command, RejectionType.INVALID_STATE, NO_EVENT_OCCURRED_MESSAGE);
      return;
    }

    // avoid reusing the subscription record directly as any access to the state (e.g. as #get)
    // will overwrite it - safer to just copy its values into an one-time-use record
    final ProcessMessageSubscriptionRecord subscriptionRecord = subscription.getRecord();
    record
        .setElementId(subscriptionRecord.getElementIdBuffer())
        .setInterrupting(subscriptionRecord.isInterrupting())
        .setRootProcessInstanceKey(subscriptionRecord.getRootProcessInstanceKey())
        .setStorageOrdinalKey(subscriptionRecord.getStorageOrdinalKey())
        // The correlate command sent from the message partition does not carry the businessId; it
        // was captured from the subscribing process instance at subscription-open time and
        // persisted on the subscription.
        // Copy it onto the CORRELATED event so the correlated-message read path can expose the
        // businessId for catch / boundary / intermediate correlations too.
        .setBusinessId(subscriptionRecord.getBusinessIdBuffer());

    stateWriter.appendFollowUpEvent(
        subscription.getKey(), ProcessMessageSubscriptionIntent.CORRELATED, record);

    // update transient state in a side-effect to ensure that these changes only take effect
    // after
    // the command has been successfully processed
    sideEffectWriter.appendSideEffect(
        () -> {
          transientProcessMessageSubscriptionState.remove(
              new PendingSubscription(elementInstanceKey, messageName, tenantId));
        });

    final var catchEvent = getCatchEvent(elementInstance.getValue(), record.getElementIdBuffer());
    eventHandle.activateElement(
        catchEvent, elementInstanceKey, elementInstance.getValue(), record.getVariablesBuffer());

    sendAcknowledgeCommand(record);
  }

  /**
   * A correlate is stale when it quotes a message-side subscription key that no longer matches the
   * key stored on the PI side. Both keys must be set (non-default): unkeyed/legacy subscriptions
   * default to -1 and are never treated as stale, preserving backwards compatibility.
   */
  private boolean isStaleGeneration(
      final ProcessMessageSubscriptionRecord command,
      final ProcessMessageSubscription subscription) {
    final long commandKey = command.getSubscriptionKey();
    final long storedKey = subscription.getRecord().getSubscriptionKey();
    return commandKey != -1L && storedKey != -1L && commandKey != storedKey;
  }

  private boolean hasAlreadyBeenCorrelated(
      final ProcessMessageSubscriptionRecord record,
      final ProcessMessageSubscription subscription) {
    // we only want to correlate a subscription once per message (by key)
    final var messageKey = record.getMessageKey();

    // return true if it has already been correlated, otherwise false
    final var lastCorrelatedMessageKey = subscription.getRecord().getMessageKey();
    // as correlations are ordered per message, a message is considered to have been correlated
    // either if it is the last correlated message (keys are equal), or if it was a message prior to
    // the last correlated one (messageKey is less than lastCorrelatedMessageKey).
    return messageKey <= lastCorrelatedMessageKey;
  }

  private ExecutableFlowElement getCatchEvent(
      final ProcessInstanceRecord elementRecord, final DirectBuffer elementId) {
    return processState.getFlowElement(
        elementRecord.getProcessDefinitionKey(),
        elementRecord.getTenantId(),
        elementId,
        ExecutableFlowElement.class);
  }

  private void rejectCommand(
      final TypedRecord<ProcessMessageSubscriptionRecord> command,
      final RejectionType rejectionType,
      final String reasonTemplate) {

    final var subscription = command.getValue();
    final var reason =
        String.format(
            reasonTemplate,
            subscription.getElementInstanceKey(),
            bufferAsString(subscription.getMessageNameBuffer()));

    rejectionWriter.appendRejection(command, rejectionType, reason);

    sendRejectionCommand(subscription);
  }

  private void sendAcknowledgeCommand(final ProcessMessageSubscriptionRecord subscription) {
    subscriptionCommandSender.correlateMessageSubscription(
        subscription.getMessageKey(),
        subscription.getSubscriptionPartitionId(),
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getProcessDefinitionKey(),
        subscription.getBpmnProcessIdBuffer(),
        subscription.getMessageNameBuffer(),
        subscription.getTenantId(),
        subscription.getStorageOrdinalKey());
  }

  private void sendRejectionCommand(final ProcessMessageSubscriptionRecord subscription) {
    subscriptionCommandSender.rejectCorrelateMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getProcessDefinitionKey(),
        subscription.getBpmnProcessIdBuffer(),
        subscription.getMessageKey(),
        subscription.getMessageNameBuffer(),
        subscription.getCorrelationKeyBuffer(),
        subscription.getTenantId(),
        subscription.getSubscriptionKey(),
        subscription.getStorageOrdinalKey());
  }

  @Override
  public SuspensionBehavior suspensionBehavior(
      final TypedRecord<ProcessMessageSubscriptionRecord> record) {
    // Process unconditionally — buffering would keep the message-partition lock held
    // indefinitely while the instance is suspended or resuming. The exact-SUSPENDED check
    // inside processRecord rejects a CORRELATE that arrives during the narrow suspend/close
    // race window. During RESUMING it must fall through instead: an early reopened subscription
    // can correlate a still-buffered message while later REOPEN commands are still draining.
    return SuspensionBehavior.PROCESS;
  }
}
