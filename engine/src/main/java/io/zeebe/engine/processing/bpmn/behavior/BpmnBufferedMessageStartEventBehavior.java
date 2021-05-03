/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.EventHandle;
import io.zeebe.engine.processing.common.EventTriggerBehavior;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.immutable.MessageState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MessageState messageState;
  private final ProcessState processState;
  private final MessageStartEventSubscriptionState messageStartEventSubscriptionState;

  private final EventHandle eventHandle;

  public BpmnBufferedMessageStartEventBehavior(
      final ZeebeState zeebeState,
      final KeyGenerator keyGenerator,
      final EventTriggerBehavior eventTriggerBehavior,
      final Writers writers) {
    messageState = zeebeState.getMessageState();
    processState = zeebeState.getProcessState();
    messageStartEventSubscriptionState = zeebeState.getMessageStartEventSubscriptionState();

    eventHandle =
        new EventHandle(
            keyGenerator,
            zeebeState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior);
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
    final var process = processState.getLatestProcessVersionByProcessId(bpmnProcessId);

    findNextMessageToCorrelate(process, correlationKey)
        .ifPresent(
            messageCorrelation -> {
              final var storedMessage = messageState.getMessage(messageCorrelation.messageKey);

              eventHandle.triggerMessageStartEvent(
                  messageCorrelation.subscriptionKey,
                  messageCorrelation.subscriptionRecord,
                  storedMessage.getMessageKey(),
                  storedMessage.getMessage());
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
              messageName,
              correlationKey,
              storedMessage -> {
                // correlate the first message with same correlation key that was not correlated yet
                if (storedMessage.getMessage().getDeadline() > ActorClock.currentTimeMillis()
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

  private static class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private long subscriptionKey = -1L;
    private final MessageStartEventSubscriptionRecord subscriptionRecord =
        new MessageStartEventSubscriptionRecord();
  }
}
