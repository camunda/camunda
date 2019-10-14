/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.callactivity;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementActivatingHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;

public class CallActivityActivatingHandler
    extends ActivityElementActivatingHandler<ExecutableCallActivity> {

  public CallActivityActivatingHandler(CatchEventSubscriber catchEventSubscriber) {
    super(WorkflowInstanceIntent.ELEMENT_ACTIVATED, catchEventSubscriber);
  }

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableCallActivity> context) {
    super.handleState(context);

    final var processId = context.getElement().getCalledElementProcessId();
    final var workflow = context.getStateDb().getLatestWorkflowVersionByProcessId(processId);

    if (workflow == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to be deployed, but not found.",
              bufferAsString(processId)));
      return false;
    }

    final var noneStartEvent = workflow.getWorkflow().getNoneStartEvent();

    if (noneStartEvent == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to have a none start event, but not found.",
              bufferAsString(processId)));
      return false;
    }

    return true;
  }
}
