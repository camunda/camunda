/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.agrona.DirectBuffer;

public final class MessageCorrelateBehavior {

  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final MessageSubscriptionState messageSubscriptionState;
  private final MessageState messageState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final boolean businessIdUniquenessEnabled;
  private final EventHandle eventHandle;
  private final StateWriter stateWriter;
  private final SubscriptionCommandSender commandSender;

  public MessageCorrelateBehavior(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final EventHandle eventHandle,
      final StateWriter stateWriter,
      final MessageSubscriptionState messageSubscriptionState,
      final SubscriptionCommandSender commandSender,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final boolean businessIdUniquenessEnabled) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.messageState = messageState;
    this.eventHandle = eventHandle;
    this.stateWriter = stateWriter;
    this.commandSender = commandSender;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  public void correlateToMessageStartEvents(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    correlateToMessageStartEvents(messageData, correlatingSubscriptions, null);
  }

  public void correlateToMessageStartEvents(
      final MessageData messageData,
      final Subscriptions correlatingSubscriptions,
      final Collection<String> blockedProcessIds) {
    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageData.tenantId(),
        messageData.messageName(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var bpmnProcessIdBuffer = subscriptionRecord.getBpmnProcessIdBuffer();

          if (correlatingSubscriptions.contains(bpmnProcessIdBuffer)) {
            return;
          }

          if (shouldCorrelateStartEvent(messageData, subscriptionRecord)) {
            final var processInstanceKey =
                eventHandle.triggerMessageStartEvent(
                    subscription.getKey(),
                    subscriptionRecord,
                    messageData.messageKey(),
                    messageData.messageName(),
                    messageData.correlationKey(),
                    messageData.variables(),
                    messageData.businessId());

            subscriptionRecord.setProcessInstanceKey(processInstanceKey);
            correlatingSubscriptions.add(subscriptionRecord);
          } else if (blockedProcessIds != null) {
            blockedProcessIds.add(subscriptionRecord.getBpmnProcessId());
          }
        });
  }

  /**
   * Collects message start event subscriptions without writing state changes. Used for
   * authorization checks before actual correlation.
   */
  public void collectMessageStartEventSubscriptions(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageData.tenantId(),
        messageData.messageName(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var bpmnProcessIdBuffer = subscriptionRecord.getBpmnProcessIdBuffer();

          if (correlatingSubscriptions.contains(bpmnProcessIdBuffer)) {
            return;
          }

          // Mirror the same uniqueness gates as the real correlation path so authorization is only
          // checked for subscriptions that would actually correlate.
          if (shouldCorrelateStartEvent(messageData, subscriptionRecord)) {
            // Just collect, don't write state yet
            correlatingSubscriptions.add(subscriptionRecord);
          }
        });
  }

  public void correlateToMessageEvents(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {

    messageSubscriptionState.visitSubscriptions(
        messageData.tenantId(),
        messageData.messageName(),
        messageData.correlationKey(),
        subscription -> {

          // correlate the message only once per process
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(
                  subscription.getRecord().getBpmnProcessIdBuffer())
              && businessIdMatches(
                  messageData.businessId(), subscription.getRecord().getBusinessIdBuffer())) {

            final var correlatingSubscription =
                subscription
                    .getRecord()
                    .setMessageKey(messageData.messageKey())
                    .setVariables(messageData.variables());

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);

            correlatingSubscriptions.add(correlatingSubscription);
          }

          return true;
        });
  }

  /**
   * Collects message event subscriptions without writing state changes. Used for authorization
   * checks before actual correlation.
   */
  public void collectMessageEventSubscriptions(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    messageSubscriptionState.visitSubscriptions(
        messageData.tenantId(),
        messageData.messageName(),
        messageData.correlationKey(),
        subscription -> {

          // correlate the message only once per process
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(
                  subscription.getRecord().getBpmnProcessIdBuffer())
              && businessIdMatches(
                  messageData.businessId(), subscription.getRecord().getBusinessIdBuffer())) {

            // Just collect, don't write state yet
            correlatingSubscriptions.add(subscription.getRecord());
          }

          return true;
        });
  }

  public void sendCorrelateCommands(
      final MessageData messageData, final Subscriptions correlatingSubscriptions) {
    correlatingSubscriptions.visitSubscriptions(
        subscription ->
            commandSender.correlateProcessMessageSubscription(
                subscription.getProcessInstanceKey(),
                subscription.getElementInstanceKey(),
                subscription.getProcessDefinitionKey(),
                subscription.getBpmnProcessId(),
                messageData.messageName(),
                messageData.messageKey(),
                messageData.variables(),
                messageData.correlationKey(),
                messageData.tenantId()));
  }

  /**
   * Asymmetric business-id matching rule from the design: a message published without a business id
   * (empty buffer) correlates regardless of the subscription's stored business id; a message with a
   * business id only correlates to subscriptions whose stored business id matches exactly. Reading
   * from the local {@code MessageSubscription} (not from PI state on another partition) is what
   * keeps this a constant-time, on-partition check.
   */
  private boolean businessIdMatches(
      final DirectBuffer messageBusinessId, final DirectBuffer subscriptionBusinessId) {
    if (messageBusinessId == null || messageBusinessId.capacity() == 0) {
      return true;
    }
    return BufferUtil.equals(messageBusinessId, subscriptionBusinessId);
  }

  /**
   * Returns {@code true} when this start-event subscription should correlate for the given message:
   * only one instance per (process definition, correlation key) is created — an empty correlation
   * key allows multiple instances — and, when the businessId uniqueness feature is enabled and the
   * message carries a businessId, no active root PI on this partition may already hold that
   * businessId for this process definition.
   */
  private boolean shouldCorrelateStartEvent(
      final MessageData messageData, final MessageStartEventSubscriptionRecord subscriptionRecord) {
    final var correlationKeyFree =
        messageData.correlationKey().capacity() == 0
            || !messageState.existActiveProcessInstance(
                messageData.tenantId(),
                subscriptionRecord.getBpmnProcessIdBuffer(),
                messageData.correlationKey());
    return correlationKeyFree && !isBusinessIdAlreadyHeld(messageData, subscriptionRecord);
  }

  /**
   * Local arm of the design's start-event uniqueness check: when the published message carries a
   * businessId and the feature is enabled, reject the start when a root PI with the same businessId
   * is already active for this process definition on this partition.
   *
   * <p>A suppressed message remains in the existing message buffer and is only freed by its TTL.
   * There is no businessId-keyed retry trigger today: the existing buffered-message scan in {@link
   * io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior} is
   * driven by completion of a PI that holds the correlation-key lock, so a suppressed message whose
   * correlation key does not match an active holder's lock will not be retried even after the
   * businessId is released. Release-driven retries — both same-partition and across partitions via
   * the pull-based ask to {@code P_B} — are introduced in a later increment by design.
   *
   * <p>Cross-partition uniqueness (i.e. when the businessId hashes to a different partition than
   * the correlation key) is also intentionally out of scope here and is delegated to {@code P_B} in
   * a later increment via a cross-partition ask. This method only resolves what {@code P_K} can
   * answer locally.
   */
  private boolean isBusinessIdAlreadyHeld(
      final MessageData messageData, final MessageStartEventSubscriptionRecord subscriptionRecord) {
    if (!businessIdUniquenessEnabled) {
      return false;
    }
    final var businessId = messageData.businessId();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        BufferUtil.bufferAsString(businessId),
        subscriptionRecord.getBpmnProcessId(),
        messageData.tenantId(),
        bannedInstanceState::isProcessInstanceBanned);
  }

  public record MessageData(
      long messageKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      DirectBuffer variables,
      String tenantId,
      DirectBuffer businessId) {}
}
