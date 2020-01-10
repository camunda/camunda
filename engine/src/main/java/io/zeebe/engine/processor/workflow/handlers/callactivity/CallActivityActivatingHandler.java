/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.callactivity;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementActivatingHandler;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryProcessor;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class CallActivityActivatingHandler
    extends ActivityElementActivatingHandler<ExecutableCallActivity> {

  private final KeyGenerator keyGenerator;

  private final WorkflowInstanceRecord childInstanceRecord = new WorkflowInstanceRecord();
  private final MsgPackQueryProcessor queryProcessor = new MsgPackQueryProcessor();

  public CallActivityActivatingHandler(
      final CatchEventSubscriber catchEventSubscriber, final KeyGenerator keyGenerator) {
    super(null, catchEventSubscriber);
    this.keyGenerator = keyGenerator;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<ExecutableCallActivity> context) {
    if (!super.handleState(context)) {
      return false;
    }

    getProcessId(context)
        .map(processId -> getCalledWorkflow(processId, context))
        .ifPresent(
            workflow -> {
              transitionTo(context, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

              final var childWorkflowInstanceKey = createInstance(workflow, context);

              final var callActivityInstanceKey = context.getKey();
              copyVariables(callActivityInstanceKey, childWorkflowInstanceKey, workflow, context);

              final var callActivityInstance = context.getElementInstance();
              callActivityInstance.setCalledChildInstanceKey(childWorkflowInstanceKey);
              context.getElementInstanceState().updateInstance(callActivityInstance);
            });

    return true;
  }

  private Optional<DirectBuffer> getProcessId(
      final BpmnStepContext<ExecutableCallActivity> context) {
    final var callActivity = context.getElement();

    return callActivity
        .getCalledElementProcessId()
        .or(
            () ->
                callActivity
                    .getCalledElementProcessIdExpression()
                    .map(query -> readProcessId(query, context)));
  }

  private DirectBuffer readProcessId(
      final JsonPathQuery processIdExpression,
      final BpmnStepContext<ExecutableCallActivity> context) {

    final var variablesState = context.getElementInstanceState().getVariablesState();
    final var variables =
        variablesState.getVariablesAsDocument(
            context.getKey(), List.of(processIdExpression.getVariableName()));

    final var result = queryProcessor.process(processIdExpression, variables);
    if (result.size() < 1) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR,
          String.format(
              "Expected call activity process id variable '%s' to be a STRING, but not found.",
              bufferAsString(processIdExpression.getExpression())));
      return null;
    }

    final var singleResult = result.getSingleResult();
    if (!singleResult.isString()) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR,
          String.format(
              "Expected call activity process id variable '%s' to be a STRING, but found '%s'.",
              bufferAsString(processIdExpression.getExpression()), singleResult.getType()));
      return null;
    }

    return singleResult.getString();
  }

  private DeployedWorkflow getCalledWorkflow(
      final DirectBuffer processId, final BpmnStepContext<ExecutableCallActivity> context) {

    final var workflow = context.getStateDb().getLatestWorkflowVersionByProcessId(processId);
    if (workflow == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to be deployed, but not found.",
              bufferAsString(processId)));
      return null;
    }

    final var noneStartEvent = workflow.getWorkflow().getNoneStartEvent();
    if (noneStartEvent == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to have a none start event, but not found.",
              bufferAsString(processId)));
      return null;
    }

    return workflow;
  }

  private long createInstance(
      final DeployedWorkflow workflow, final BpmnStepContext<ExecutableCallActivity> context) {

    final var parentWorkflowInstanceKey = context.getValue().getWorkflowInstanceKey();
    final var parentElementInstanceKey = context.getKey();

    final var workflowInstanceKey = keyGenerator.nextKey();

    childInstanceRecord.reset();
    childInstanceRecord
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setVersion(workflow.getVersion())
        .setWorkflowKey(workflow.getKey())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setParentWorkflowInstanceKey(parentWorkflowInstanceKey)
        .setParentElementInstanceKey(parentElementInstanceKey);

    context
        .getOutput()
        .appendFollowUpEvent(
            workflowInstanceKey,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            childInstanceRecord,
            workflow.getWorkflow());

    return workflowInstanceKey;
  }

  private void copyVariables(
      final long source,
      final long target,
      final DeployedWorkflow targetWorkflow,
      final BpmnStepContext<ExecutableCallActivity> context) {

    final var state = context.getElementInstanceState().getVariablesState();

    final var variables = state.getVariablesAsDocument(source);
    state.setVariablesFromDocument(target, targetWorkflow.getKey(), variables);
  }
}
