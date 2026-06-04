/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior.MessageData;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MessageState messageState;
  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;
  private final ElementInstanceState elementInstanceState;
  private final BannedInstanceState bannedInstanceState;
  private final boolean businessIdUniquenessEnabled;

  private final MessageCorrelateBehavior messageCorrelateBehavior;
  private final InstantSource clock;

  public BpmnBufferedMessageStartEventBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final Writers writers,
      final SubscriptionCommandSender commandSender,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final boolean businessIdUniquenessEnabled) {
    messageState = processingState.getMessageState();
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
    elementInstanceState = processingState.getElementInstanceState();
    bannedInstanceState = processingState.getBannedInstanceState();
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    this.clock = clock;

    final var eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);

    // Reuse the live-publish correlation logic for the buffered pick-up so a buffered message whose
    // businessId belongs to another partition is re-routed through the cross-partition handshake
    // instead of being started locally (which would bypass P_B's uniqueness check).
    messageCorrelateBehavior =
        new MessageCorrelateBehavior(
            messageStartEventSubscriptionState,
            messageState,
            eventHandle,
            writers.state(),
            processingState.getMessageSubscriptionState(),
            commandSender,
            elementInstanceState,
            bannedInstanceState,
            businessIdUniquenessEnabled,
            routingInfo,
            processingState.getPartitionId());
  }

  public Optional<DirectBuffer> findCorrelationKey(final BpmnElementContext context) {
    final var processInstanceKey = context.getProcessInstanceKey();
    return Optional.ofNullable(messageState.getProcessInstanceCorrelationKey(processInstanceKey));
  }

  public void correlateMessage(
      final BpmnElementContext context, final DirectBuffer correlationKey) {

    if (correlationKey != null) {
      // the process instance was created by a message with a correlation key
      // - other messages with same correlation key are not correlated to this process until this
      // instance is ended (process-correlation-key lock)
      // - now, after the instance is ended, correlate the next buffered message
      correlateNextBufferedMessage(
          context.getBpmnProcessId(), correlationKey, context.getTenantId());
    }
  }

  /**
   * Picks the next eligible buffered message-start message for the given {@code (process,
   * correlationKey)} and triggers it, if any.
   *
   * <p>Takes {@code bpmnProcessId} and {@code tenantId} directly rather than a {@link
   * BpmnElementContext} so it can be driven from contexts that have no completing element instance
   * to hand. Today the local process-correlation-key lock release runs on process-instance
   * completion (which supplies a context); the cross-partition lock release runs from a poll
   * response on {@code P_K} (which does not). Both need the same buffer pick-up behaviour.
   */
  public void correlateNextBufferedMessage(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey, final String tenantId) {

    final var process = processState.getLatestProcessVersionByProcessId(bpmnProcessId, tenantId);

    findNextMessageToCorrelate(process, correlationKey)
        .ifPresent(
            messageCorrelation -> {
              final var storedMessage = messageState.getMessage(messageCorrelation.messageKey);
              final var message = storedMessage.getMessage();

              // Route the picked-up message exactly as a fresh publish would be: a businessId that
              // hashes to another partition is delegated to P_B via the cross-partition ask rather
              // than started locally, so the buffered pick-up cannot bypass the uniqueness
              // handshake
              // or violate the invariant that every businessId-carrying PI lives on P_B.
              messageCorrelateBehavior.triggerOrDelegateStartEvent(
                  new MessageData(
                      storedMessage.getMessageKey(),
                      message.getNameBuffer(),
                      message.getCorrelationKeyBuffer(),
                      message.getVariablesBuffer(),
                      message.getTenantId(),
                      message.getBusinessIdBuffer(),
                      message.getDeadline()),
                  messageCorrelation.subscriptionKey,
                  messageCorrelation.subscriptionRecord);
            });
  }

  private Optional<Correlation> findNextMessageToCorrelate(
      final DeployedProcess process, final DirectBuffer correlationKey) {

    final var messageCorrelation = new Correlation();
    final var bpmnProcessId = BufferUtil.bufferAsString(process.getBpmnProcessId());

    messageStartEventSubscriptionState.visitSubscriptionsByProcessDefinition(
        process.getKey(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var messageName = subscriptionRecord.getMessageNameBuffer();

          messageState.visitMessages(
              subscriptionRecord.getTenantId(),
              messageName,
              correlationKey,
              storedMessage -> {
                // correlate the first message with same correlation key that was not correlated
                // yet. Additionally, when the feature is enabled, skip a buffered message whose
                // businessId is currently taken by another active PI on this partition; the
                // visitor returns true so the scan continues to subsequent buffered entries for
                // the same correlation key — the queue itself is not stalled, only the skipped
                // entry is left in the buffer until its TTL or until another K-keyed completion
                // triggers a rescan. A businessId-keyed retry (so the skipped entry can be picked
                // up the moment the holding PI ends, regardless of correlation key) is the job of
                // the release-driven retry path added in a later increment.
                if (storedMessage.getMessage().getDeadline() > clock.millis()
                    && !messageState.existMessageCorrelation(
                        storedMessage.getMessageKey(), process.getBpmnProcessId())
                    && !isBusinessIdAlreadyHeld(storedMessage, bpmnProcessId)) {

                  // correlate the first published message across all message start events
                  // - using the message key to decide which message was published before
                  if (storedMessage.getMessageKey() < messageCorrelation.messageKey) {
                    messageCorrelation.messageKey = storedMessage.getMessageKey();
                    messageCorrelation.subscriptionKey = subscription.getKey();
                    messageCorrelation.subscriptionRecord.wrap(subscription.getRecord());
                  }

                  return false;
                }

                return true;
              });
        });

    if (messageCorrelation.subscriptionKey > 0) {
      return Optional.of(messageCorrelation);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Returns {@code true} when the feature is enabled, the buffered message carries a businessId,
   * and an active root PI on this partition already holds that businessId for this process
   * definition. See the class JavaDoc of {@link
   * io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior} for the system-level
   * retry/cross-partition narrative this predicate participates in.
   */
  private boolean isBusinessIdAlreadyHeld(
      final StoredMessage storedMessage, final String bpmnProcessId) {
    if (!businessIdUniquenessEnabled) {
      return false;
    }
    final var businessId = storedMessage.getMessage().getBusinessIdBuffer();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        BufferUtil.bufferAsString(businessId),
        bpmnProcessId,
        storedMessage.getMessage().getTenantId(),
        bannedInstanceState::isProcessInstanceBanned);
  }

  private static final class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private long subscriptionKey = -1L;
    private final MessageStartEventSubscriptionRecord subscriptionRecord =
        new MessageStartEventSubscriptionRecord();
  }
}
