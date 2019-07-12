/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

public class StartEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '%d', but no such workflow was found";
  private static final String NO_TRIGGERED_EVENT_MESSAGE = "No triggered event for workflow '%d'";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final WorkflowState state;
  private final KeyGenerator keyGenerator;

  public StartEventEventOccurredHandler(ZeebeState zeebeState) {
    this(null, zeebeState);
  }

  public StartEventEventOccurredHandler(WorkflowInstanceIntent nextState, ZeebeState zeebeState) {
    super(nextState);
    this.state = zeebeState.getWorkflowState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getValue();
    final long workflowKey = event.getWorkflowKey();
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    final long workflowInstanceKey = keyGenerator.nextKey();

    // this should never happen because workflows are never deleted.
    if (workflow == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          String.format(NO_WORKFLOW_FOUND_MESSAGE, workflowKey));
      return false;
    }

    final EventTrigger triggeredEvent = getTriggeredEvent(context, workflowKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(
          String.format(NO_TRIGGERED_EVENT_MESSAGE, workflowKey));
      return false;
    }

    createWorkflowInstance(context, workflow, workflowInstanceKey);
    final WorkflowInstanceRecord record =
        getEventRecord(context, triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setVersion(workflow.getVersion())
            .setBpmnProcessId(workflow.getBpmnProcessId())
            .setFlowScopeKey(workflowInstanceKey);

    deferEvent(context, workflowKey, workflowInstanceKey, record, triggeredEvent);
    return true;
  }

  private void createWorkflowInstance(
      BpmnStepContext<T> context, DeployedWorkflow workflow, long workflowInstanceKey) {
    record
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setWorkflowKey(workflow.getKey())
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(workflowInstanceKey);

    context
        .getOutput()
        .appendFollowUpEvent(
            workflowInstanceKey,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            record,
            workflow.getWorkflow());
  }

  private void deferStartEventRecord(
      BpmnStepContext<T> context, long workflowInstanceKey, WorkflowInstanceRecord event) {
    event.setWorkflowInstanceKey(workflowInstanceKey);
    event.setFlowScopeKey(workflowInstanceKey);

    context
        .getOutput()
        .deferRecord(workflowInstanceKey, event, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }
}
