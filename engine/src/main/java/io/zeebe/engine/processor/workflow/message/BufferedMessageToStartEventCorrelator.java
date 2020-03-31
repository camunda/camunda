/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventHandle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.handlers.container.WorkflowPostProcessor;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;

public final class BufferedMessageToStartEventCorrelator implements WorkflowPostProcessor {

  private final MessageState messageState;
  private final EventHandle eventHandle;

  private final Correlation messageCorrelation = new Correlation();

  public BufferedMessageToStartEventCorrelator(
      final KeyGenerator keyGenerator,
      final MessageState messageState,
      final EventScopeInstanceState eventScopeInstanceState) {
    this.messageState = messageState;

    eventHandle = new EventHandle(keyGenerator, eventScopeInstanceState);
  }

  @Override
  public void accept(final BpmnStepContext<ExecutableFlowElementContainer> context) {

    final var workflowInstanceKey = context.getValue().getWorkflowInstanceKey();
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
      final DirectBuffer correlationKey,
      final BpmnStepContext<ExecutableFlowElementContainer> context) {

    final var bpmnProcessId = context.getValue().getBpmnProcessIdBuffer();
    final var workflow = context.getStateDb().getLatestWorkflowVersionByProcessId(bpmnProcessId);

    final var messageCorrelation = findNextMessageToCorrelate(workflow, correlationKey);

    if (messageCorrelation == null) {
      // no buffered message to correlate
      // - release the workflow-correlation-key lock
      messageState.removeActiveWorkflowInstance(bpmnProcessId, correlationKey);

    } else {
      final var message = messageState.getMessage(messageCorrelation.messageKey);

      correlateMessage(workflow, messageCorrelation.elementId, message, context);
    }
  }

  private Correlation findNextMessageToCorrelate(
      final DeployedWorkflow workflow, final DirectBuffer correlationKey) {

    messageCorrelation.messageKey = Long.MAX_VALUE;
    messageCorrelation.elementId = null;

    for (final ExecutableStartEvent startEvent : workflow.getWorkflow().getStartEvents()) {
      if (startEvent.isMessage()) {

        final var messageName = startEvent.getMessage().getMessageName();

        messageState.visitMessages(
            messageName,
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

    return messageCorrelation.elementId != null ? messageCorrelation : null;
  }

  private void correlateMessage(
      final DeployedWorkflow workflow,
      final DirectBuffer elementId,
      final Message message,
      final BpmnStepContext<ExecutableFlowElementContainer> context) {

    final var workflowInstanceKey =
        eventHandle.triggerStartEvent(
            context.getOutput().getStreamWriter(),
            workflow.getKey(),
            elementId,
            message.getVariables());

    if (workflowInstanceKey > 0) {
      // mark the message as correlated
      messageState.putMessageCorrelation(message.getKey(), workflow.getBpmnProcessId());
      messageState.putWorkflowInstanceCorrelationKey(
          workflowInstanceKey, message.getCorrelationKey());
    }
  }

  private static class Correlation {
    private long messageKey;
    private DirectBuffer elementId;
  }
}
