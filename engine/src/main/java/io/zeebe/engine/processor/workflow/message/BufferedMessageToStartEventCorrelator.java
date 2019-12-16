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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.handlers.container.WorkflowPostProcessor;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.sched.clock.ActorClock;
import org.agrona.DirectBuffer;

public final class BufferedMessageToStartEventCorrelator implements WorkflowPostProcessor {

  private final MessageState messageState;
  private final KeyGenerator keyGenerator;

  private final WorkflowInstanceRecord startEventRecord =
      new WorkflowInstanceRecord().setBpmnElementType(BpmnElementType.START_EVENT);

  private final Correlation messageCorrelation = new Correlation();

  public BufferedMessageToStartEventCorrelator(
      final KeyGenerator keyGenerator, final MessageState messageState) {
    this.keyGenerator = keyGenerator;
    this.messageState = messageState;
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

    createEventTrigger(workflow, elementId, message, context);
    final long workflowInstanceKey = createNewWorkflowInstance(workflow, elementId, context);

    // mark the message as correlated
    messageState.putMessageCorrelation(message.getKey(), workflow.getBpmnProcessId());
    messageState.putWorkflowInstanceCorrelationKey(
        workflowInstanceKey, message.getCorrelationKey());
  }

  private void createEventTrigger(
      final DeployedWorkflow workflow,
      final DirectBuffer elementId,
      final Message message,
      final BpmnStepContext<ExecutableFlowElementContainer> context) {

    final boolean success =
        context
            .getStateDb()
            .getEventScopeInstanceState()
            .triggerEvent(workflow.getKey(), message.getKey(), elementId, message.getVariables());

    if (!success) {
      throw new IllegalStateException(
          String.format(
              "Expected the event trigger for be created of the workflow with key '%d' but failed.",
              workflow.getKey()));
    }
  }

  private long createNewWorkflowInstance(
      final DeployedWorkflow workflow,
      final DirectBuffer startEventElementId,
      final BpmnStepContext<ExecutableFlowElementContainer> context) {

    final var workflowInstanceKey = keyGenerator.nextKey();
    final var eventKey = keyGenerator.nextKey();

    context
        .getOutput()
        .appendFollowUpEvent(
            eventKey,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            startEventRecord
                .setWorkflowKey(workflow.getKey())
                .setWorkflowInstanceKey(workflowInstanceKey)
                .setElementId(startEventElementId));

    return workflowInstanceKey;
  }

  private static class Correlation {
    private long messageKey;
    private DirectBuffer elementId;
  }
}
