/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.bpmn.behavior;

import io.camunda.zeebe.engine.common.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.common.processing.common.EventHandle;
import io.camunda.zeebe.engine.common.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.common.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.common.state.immutable.MessageState;
import io.camunda.zeebe.engine.common.state.immutable.ProcessState;
import io.camunda.zeebe.engine.common.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MessageState messageState;
  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;

  private final EventHandle eventHandle;
  private final InstantSource clock;

  public BpmnBufferedMessageStartEventBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final Writers writers,
      final InstantSource clock) {
    messageState = processingState.getMessageState();
    processState = processingState.getProcessState();
    messageStartEventSubscriptionState = processingState.getMessageStartEventSubscriptionState();
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
                  storedMessage.getMessage().getVariablesBuffer());
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
                // correlate the first message with same correlation key that was not correlated yet
                if (storedMessage.getMessage().getDeadline() > clock.millis()
                    && !messageState.existMessageCorrelation(
                        storedMessage.getMessageKey(), process.getBpmnProcessId())) {

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

  private static final class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private long subscriptionKey = -1L;
    private final MessageStartEventSubscriptionRecord subscriptionRecord =
        new MessageStartEventSubscriptionRecord();
  }
}
