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
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * Correlates published messages to message start-event subscriptions and to intermediate message
 * catch-event subscriptions on this partition.
 *
 * <h3>Business-id uniqueness — same-partition and cross-partition arms</h3>
 *
 * When the {@code businessIdUniquenessEnabled} feature is on and a published message carries a
 * businessId, start-event correlation routes through one of two arms depending on whether the
 * businessId hashes to the partition that owns the message ({@code P_K = hash(correlationKey)}) or
 * to a different partition ({@code P_B = hash(businessId)}):
 *
 * <ul>
 *   <li><b>Same-partition arm</b> ({@code P_B == P_K}): uniqueness is checked locally via {@link
 *       ElementInstanceState#hasActiveProcessInstanceWithBusinessId(String, String, String,
 *       Predicate)}. A conflict suppresses the start; the message remains in the buffer and is only
 *       freed by its TTL — no businessId-keyed retry trigger exists today on this path either.
 *   <li><b>Cross-partition arm</b> ({@code P_B != P_K}): ownership of the uniqueness check is
 *       delegated to {@code P_B} via a dedicated cross-partition request. {@code P_K} dispatches a
 *       {@link MessageStartProcessInstanceRequestIntent#REQUEST} command to {@code P_B} and writes
 *       a local {@link MessageStartProcessInstanceRequestIntent#REQUESTED} follow-up event whose
 *       applier persists a pending-ask entry — so a dropped reply is recoverable via the retry
 *       scheduler. The actual PI activation and the three reply outcomes (STARTED /
 *       UNIQUENESS_REJECTED / NO_SUBSCRIPTION_REJECTED) are handled by {@code P_B} and the reply
 *       processors on {@code P_K}.
 * </ul>
 *
 * <h3>Lock-release semantics for cross-partition starts</h3>
 *
 * For PIs created via the cross-partition ask, {@code P_K} writes a process-correlation-key lock
 * entry locally on STARTED and records the holder's {@code (processId, businessId)} on that entry
 * (today: {@code (bpmnProcessId, correlationKey)} in the lock CF, businessId in a parallel
 * discriminator CF). The partition {@code P_B = hash(businessId)} is derived from {@code
 * businessId} at query time and is not stored. Subsequent publishes on {@code P_K} that share the
 * same {@code correlationKey} are buffered exactly as today, regardless of their {@code businessId}
 * — preserving the pre-existing contract that the process-correlation-key lock blocks all further
 * triggers while a holder PI is alive. The lock is released when the pull query introduced in a
 * later increment reports {@code (processId, businessId)} as available; this is the same query that
 * releases the Business ID uniqueness lock on {@code P_B}, since both locks are released by the
 * same PI-completion event. PIs created locally on {@code P_K} (publishes without a {@code
 * businessId}, or with a businessId hashing to the same partition) keep using today's local release
 * path unchanged.
 *
 * <h3>Invariant established by the cross-partition arm</h3>
 *
 * Every active root PI carrying a {@code businessId} lives on {@code P_B = hash(businessId)}. This
 * is what allows the uniqueness check to remain a constant-time, on-partition index lookup. From
 * this commit onwards both creation paths — {@code CreateProcessInstance} and message-triggered
 * start events — honor this invariant.
 */
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
  private final RoutingInfo routingInfo;
  private final int partitionId;

  private final MessageStartProcessInstanceRequestRecord askRecord =
      new MessageStartProcessInstanceRequestRecord();

  public MessageCorrelateBehavior(
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final EventHandle eventHandle,
      final StateWriter stateWriter,
      final MessageSubscriptionState messageSubscriptionState,
      final SubscriptionCommandSender commandSender,
      final ElementInstanceState elementInstanceState,
      final BannedInstanceState bannedInstanceState,
      final boolean businessIdUniquenessEnabled,
      final RoutingInfo routingInfo,
      final int partitionId) {
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.messageState = messageState;
    this.eventHandle = eventHandle;
    this.stateWriter = stateWriter;
    this.commandSender = commandSender;
    this.elementInstanceState = elementInstanceState;
    this.bannedInstanceState = bannedInstanceState;
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    this.routingInfo = routingInfo;
    this.partitionId = partitionId;
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
            if (shouldDelegateToBusinessIdPartition(messageData)) {
              // Cross-partition arm: hand uniqueness ownership to P_B. The local pending-ask entry
              // (written by the REQUESTED applier on P_K) is what makes a dropped reply
              // recoverable via the retry scheduler. The actual PI activation and the three reply
              // outcomes are handled by P_B and the reply processors on P_K.
              dispatchCrossPartitionStartProcessInstanceAsk(
                  messageData, subscription.getKey(), subscriptionRecord);
              // Do not add to correlatingSubscriptions — the message has not been correlated yet
              // from this partition's point of view. The CORRELATED event is written by the STARTED
              // reply processor only after P_B confirms the activation.
              return;
            }

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
                subscription.processInstanceKey(),
                subscription.elementInstanceKey(),
                subscription.processDefinitionKey(),
                subscription.bpmnProcessId(),
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
   * Returns {@code true} when the feature is enabled, the message carries a businessId, and an
   * active root PI on this partition already holds that businessId for this process definition. See
   * the class JavaDoc for the system-level retry/cross-partition narrative.
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

  /**
   * The cross-partition arm is engaged only when (i) the feature is enabled, (ii) the publish
   * carries a non-empty {@code businessId}, and (iii) the businessId hashes to a partition other
   * than the one that owns the message. The partition lookup is the same {@code hash-mod} used for
   * correlation keys so {@code hash(businessId)} and {@code hash(correlationKey)} share a routing
   * policy.
   */
  private boolean shouldDelegateToBusinessIdPartition(final MessageData messageData) {
    if (!businessIdUniquenessEnabled) {
      return false;
    }
    final var businessId = messageData.businessId();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return routingInfo.partitionForCorrelationKey(businessId) != partitionId;
  }

  /**
   * Writes a local {@link MessageStartProcessInstanceRequestIntent#REQUESTED} follow-up event so
   * the applier persists the pending-ask entry — the retry scheduler reads this on the next tick
   * and re-sends the ask if no reply has cleared it. Immediately dispatches the actual {@link
   * MessageStartProcessInstanceRequestIntent#REQUEST} command to {@code P_B}; the command sender
   * uses a side-effect to bridge to the inter-partition transport, so the cross-partition send
   * happens only after this stream is committed (the local pending-ask entry is therefore always
   * visible before the first ask leaves the partition).
   */
  private void dispatchCrossPartitionStartProcessInstanceAsk(
      final MessageData messageData,
      final long messageStartEventSubscriptionKey,
      final MessageStartEventSubscriptionRecord subscriptionRecord) {
    askRecord.reset();
    askRecord
        .setMessageKey(messageData.messageKey())
        .setMessageName(messageData.messageName())
        .setCorrelationKey(messageData.correlationKey())
        .setBusinessId(messageData.businessId())
        .setProcessDefinitionKey(subscriptionRecord.getProcessDefinitionKey())
        .setBpmnProcessId(subscriptionRecord.getBpmnProcessIdBuffer())
        .setStartEventId(subscriptionRecord.getStartEventIdBuffer())
        .setMessageStartEventSubscriptionKey(messageStartEventSubscriptionKey)
        .setVariables(messageData.variables())
        .setTenantId(messageData.tenantId());

    stateWriter.appendFollowUpEvent(
        messageData.messageKey(), MessageStartProcessInstanceRequestIntent.REQUESTED, askRecord);

    commandSender.sendStartProcessInstanceRequest(
        routingInfo.partitionForCorrelationKey(messageData.businessId()),
        messageData.messageKey(),
        messageData.messageName(),
        messageData.correlationKey(),
        messageData.businessId(),
        subscriptionRecord.getProcessDefinitionKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getStartEventIdBuffer(),
        messageStartEventSubscriptionKey,
        messageData.variables(),
        messageData.tenantId());
  }

  public record MessageData(
      long messageKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      DirectBuffer variables,
      String tenantId,
      DirectBuffer businessId) {}
}
