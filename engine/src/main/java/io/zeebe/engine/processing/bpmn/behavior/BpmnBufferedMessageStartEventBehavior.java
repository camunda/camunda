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
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MutableMessageState messageState;
  private final ProcessState processState;
  private final TypedStreamWriter streamWriter;

  private final EventHandle eventHandle;

  public BpmnBufferedMessageStartEventBehavior(
      final ZeebeState zeebeState, final TypedStreamWriter streamWriter) {
    messageState = zeebeState.getMessageState();
    processState = zeebeState.getProcessState();
    this.streamWriter = streamWriter;

    eventHandle =
        new EventHandle(zeebeState.getKeyGenerator(), zeebeState.getEventScopeInstanceState());
  }

  public void correlateMessage(final BpmnElementContext context) {

    final var processInstanceKey = context.getProcessInstanceKey();
    final var correlationKey = messageState.getProcessInstanceCorrelationKey(processInstanceKey);

    if (correlationKey != null) {
      messageState.removeProcessInstanceCorrelationKey(processInstanceKey);

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
        .ifPresentOrElse(
            messageCorrelation -> {
              final var storedMessage = messageState.getMessage(messageCorrelation.messageKey);
              correlateMessage(process, messageCorrelation.elementId, storedMessage);
            },
            () -> {
              // no buffered message to correlate
              // - release the process-correlation-key lock
              messageState.removeActiveProcessInstance(bpmnProcessId, correlationKey);
            });
  }

  private Optional<Correlation> findNextMessageToCorrelate(
      final DeployedProcess process, final DirectBuffer correlationKey) {

    final var messageCorrelation = new Correlation();

    for (final ExecutableStartEvent startEvent : process.getProcess().getStartEvents()) {
      if (startEvent.isMessage()) {

        final DirectBuffer messageNameBuffer =
            startEvent.getMessage().getMessageName().map(BufferUtil::wrapString).orElseThrow();

        messageState.visitMessages(
            messageNameBuffer,
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
                  messageCorrelation.elementId = startEvent.getId();
                }

                return false;
              }

              return true;
            });
      }
    }

    if (messageCorrelation.elementId != null) {
      return Optional.of(messageCorrelation);
    } else {
      return Optional.empty();
    }
  }

  private void correlateMessage(
      final DeployedProcess process,
      final DirectBuffer elementId,
      final StoredMessage storedMessage) {

    final var processInstanceKey =
        eventHandle.triggerStartEvent(
            streamWriter,
            process.getKey(),
            elementId,
            storedMessage.getMessage().getVariablesBuffer());

    if (processInstanceKey > 0) {
      // mark the message as correlated
      messageState.putMessageCorrelation(storedMessage.getMessageKey(), process.getBpmnProcessId());
      messageState.putProcessInstanceCorrelationKey(
          processInstanceKey, storedMessage.getMessage().getCorrelationKeyBuffer());
    }
  }

  private static class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private DirectBuffer elementId = null;
  }
}
