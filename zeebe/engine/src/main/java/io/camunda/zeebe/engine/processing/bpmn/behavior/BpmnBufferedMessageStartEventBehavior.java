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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
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

  private final EventHandle eventHandle;
  private final InstantSource clock;

  public BpmnBufferedMessageStartEventBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final Writers writers,
      final InstantSource clock,
      final boolean businessIdUniquenessEnabled) {
    messageState = processingState.getMessageState();
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
    elementInstanceState = processingState.getElementInstanceState();
    bannedInstanceState = processingState.getBannedInstanceState();
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    this.clock = clock;

    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
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
      correlateNextBufferedMessage(correlationKey, context);
    }
  }

  private void correlateNextBufferedMessage(
      final DirectBuffer correlationKey, final BpmnElementContext context) {

    final var bpmnProcessId = context.getBpmnProcessId();
    final var process =
        processState.getLatestProcessVersionByProcessId(bpmnProcessId, context.getTenantId());

    findNextMessageToCorrelate(process, correlationKey)
        .ifPresent(
            messageCorrelation -> {
              final var storedMessage = messageState.getMessage(messageCorrelation.messageKey);

              eventHandle.triggerMessageStartEvent(
                  messageCorrelation.subscriptionKey,
                  messageCorrelation.subscriptionRecord,
                  storedMessage.getMessageKey(),
                  storedMessage.getMessage().getNameBuffer(),
                  storedMessage.getMessage().getCorrelationKeyBuffer(),
                  storedMessage.getMessage().getVariablesBuffer(),
                  storedMessage.getMessage().getBusinessIdBuffer());
            });
  }

  private Optional<Correlation> findNextMessageToCorrelate(
      final DeployedProcess process, final DirectBuffer correlationKey) {

    final var messageCorrelation = new Correlation();

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
                    && !isBusinessIdAlreadyHeld(storedMessage, process)) {

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
   * and an active root PI on this partition already holds that businessId for the same process
   * definition. A {@code true} result causes the buffered-message scan to skip this entry and keep
   * looking; the entry remains in the buffer subject to its TTL. There is no businessId-keyed
   * retrigger here: the scan only fires on completion of a PI that holds the correlation-key lock,
   * so an entry skipped because of a long-lived businessId holder on a different correlation key
   * can be left untouched until TTL. The release-driven retry path added in a later increment
   * closes that gap.
   */
  private boolean isBusinessIdAlreadyHeld(
      final StoredMessage storedMessage, final DeployedProcess process) {
    if (!businessIdUniquenessEnabled) {
      return false;
    }
    final var businessId = storedMessage.getMessage().getBusinessIdBuffer();
    if (businessId == null || businessId.capacity() == 0) {
      return false;
    }
    return elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        BufferUtil.bufferAsString(businessId),
        BufferUtil.bufferAsString(process.getBpmnProcessId()),
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
