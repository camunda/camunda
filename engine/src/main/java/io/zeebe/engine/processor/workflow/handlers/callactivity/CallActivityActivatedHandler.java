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
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class CallActivityActivatedHandler extends ElementActivatedHandler<ExecutableCallActivity> {

  private final KeyGenerator keyGenerator;

  private final WorkflowInstanceRecord childInstanceRecord = new WorkflowInstanceRecord();

  public CallActivityActivatedHandler(KeyGenerator keyGenerator) {
    super(null);
    this.keyGenerator = keyGenerator;
  }

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableCallActivity> context) {
    super.handleState(context);

    final var processId = context.getElement().getCalledElementProcessId();
    final var workflow = context.getStateDb().getLatestWorkflowVersionByProcessId(processId);

    if (workflow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected workflow with BPMN process id '%s' to be deployed, but not found.",
              bufferAsString(processId)));
    }

    createInstance(workflow, context);
    return true;
  }

  private void createInstance(
      DeployedWorkflow workflow, BpmnStepContext<ExecutableCallActivity> context) {

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
  }
}
