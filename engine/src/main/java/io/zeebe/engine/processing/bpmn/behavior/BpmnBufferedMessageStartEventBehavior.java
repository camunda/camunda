/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.EventHandle;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.immutable.WorkflowState;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnBufferedMessageStartEventBehavior {

  private final MutableMessageState messageState;
  private final WorkflowState workflowState;
  private final TypedStreamWriter streamWriter;

  private final EventHandle eventHandle;

  public BpmnBufferedMessageStartEventBehavior(
      final ZeebeState zeebeState, final TypedStreamWriter streamWriter) {
    messageState = zeebeState.getMessageState();
    workflowState = zeebeState.getWorkflowState();
    this.streamWriter = streamWriter;

    eventHandle =
        new EventHandle(zeebeState.getKeyGenerator(), zeebeState.getEventScopeInstanceState());
  }

  public void correlateMessage(final BpmnElementContext context) {

    final var workflowInstanceKey = context.getWorkflowInstanceKey();
    final var correlationKey = messageState.getWorkflowInstanceCorrelationKey(workflowInstanceKey);

    if (correlationKey != null) {
      messageState.removeWorkflowInstanceCorrelationKey(workflowInstanceKey);

      // the workflow instance was created by a message with a correlation key
      // - other messages with same correlation key are not correlated to this workflow until this
      // instance is ended (workflow-correlation-key lock)
      // - now, after the instance is ended, correlate the next buffered message
      correlateNextBufferedMessage(correlationKey, context);
    }
  }

  private void correlateNextBufferedMessage(
      final DirectBuffer correlationKey, final BpmnElementContext context) {

    final var bpmnProcessId = context.getBpmnProcessId();
    final var workflow = workflowState.getLatestWorkflowVersionByProcessId(bpmnProcessId);

    findNextMessageToCorrelate(workflow, correlationKey)
        .ifPresentOrElse(
            messageCorrelation -> {
              final var message = messageState.getMessage(messageCorrelation.messageKey);
              correlateMessage(workflow, messageCorrelation.elementId, message);
            },
            () -> {
              // no buffered message to correlate
              // - release the workflow-correlation-key lock
              messageState.removeActiveWorkflowInstance(bpmnProcessId, correlationKey);
            });
  }

  private Optional<Correlation> findNextMessageToCorrelate(
      final DeployedWorkflow workflow, final DirectBuffer correlationKey) {

    final var messageCorrelation = new Correlation();

    for (final ExecutableStartEvent startEvent : workflow.getWorkflow().getStartEvents()) {
      if (startEvent.isMessage()) {

        final DirectBuffer messageNameBuffer =
            startEvent.getMessage().getMessageName().map(BufferUtil::wrapString).orElseThrow();

        messageState.visitMessages(
            messageNameBuffer,
            correlationKey,
            message -> {
              // correlate the first message with same correlation key that was not correlated yet
              if (message.getDeadline() > ActorClock.currentTimeMillis()
                  && !messageState.existMessageCorrelation(
                      message.getKey(), workflow.getBpmnProcessId())) {

                // correlate the first published message across all message start events
                // - using the message key to decide which message was published before
                if (message.getKey() < messageCorrelation.messageKey) {
                  messageCorrelation.messageKey = message.getKey();
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
      final DeployedWorkflow workflow, final DirectBuffer elementId, final Message message) {

    final var workflowInstanceKey =
        eventHandle.triggerStartEvent(
            streamWriter, workflow.getKey(), elementId, message.getVariables());

    if (workflowInstanceKey > 0) {
      // mark the message as correlated
      messageState.putMessageCorrelation(message.getKey(), workflow.getBpmnProcessId());
      messageState.putWorkflowInstanceCorrelationKey(
          workflowInstanceKey, message.getCorrelationKey());
    }
  }

  private static class Correlation {
    private long messageKey = Long.MAX_VALUE;
    private DirectBuffer elementId = null;
  }
}
