/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.EventHandle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobErrorThrownProcessor implements TypedRecordProcessor<JobRecord> {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final CatchEventTuple catchEventTuple = new CatchEventTuple();

  private final WorkflowState workflowState;
  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final EventHandle eventHandle;
  private final JobState jobState;

  public JobErrorThrownProcessor(
      final WorkflowState workflowState, final KeyGenerator keyGenerator, final JobState jobState) {
    this.workflowState = workflowState;
    elementInstanceState = workflowState.getElementInstanceState();
    eventScopeInstanceState = workflowState.getEventScopeInstanceState();
    this.jobState = jobState;

    eventHandle = new EventHandle(keyGenerator, eventScopeInstanceState);
  }

  @Override
  public void processRecord(
      final TypedRecord<JobRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final var job = record.getValue();
    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    if (serviceTaskInstance != null && serviceTaskInstance.isActive()) {

      final var errorCode = job.getErrorCodeBuffer();

      final var foundCatchEvent = findCatchEvent(errorCode, serviceTaskInstance);
      if (foundCatchEvent != null) {

        eventHandle.triggerEvent(
            streamWriter, foundCatchEvent.instance, foundCatchEvent.catchEvent, NO_VARIABLES);

        // remove job reference to not cancel it while terminating the task
        serviceTaskInstance.setJobKey(-1L);
        elementInstanceState.updateInstance(serviceTaskInstance);

        // remove job from state
        jobState.throwError(record.getKey(), job);

      } else {
        // mark job as failed and create an incident
        job.setRetries(0);
        jobState.fail(record.getKey(), job);

        raiseIncident(record.getKey(), job, streamWriter);
      }
    }
  }

  private ExecutableWorkflow getWorkflow(final long workflowKey) {

    final var deployedWorkflow = workflowState.getWorkflowByKey(workflowKey);
    if (deployedWorkflow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected workflow with key '%d' to be deployed but not found", workflowKey));
    }

    return deployedWorkflow.getWorkflow();
  }

  private CatchEventTuple findCatchEvent(final DirectBuffer errorCode, ElementInstance instance) {
    // assuming that error events are used rarely
    // - just walk through the scope hierarchy and look for a matching catch event

    do {
      final var instanceRecord = instance.getValue();
      final var workflow = getWorkflow(instanceRecord.getWorkflowKey());

      final var found = findCatchEventInWorkflow(errorCode, workflow, instance);
      if (found != null) {
        return found;
      }

      // find in parent workflow instance if exists
      final var parentElementInstanceKey = instanceRecord.getParentElementInstanceKey();
      instance = elementInstanceState.getInstance(parentElementInstanceKey);

    } while (instance != null && instance.isActive());

    // no matching catch event found
    return null;
  }

  private CatchEventTuple findCatchEventInWorkflow(
      final DirectBuffer errorCode, final ExecutableWorkflow workflow, ElementInstance instance) {

    do {
      final var found = findCatchEventInScope(errorCode, workflow, instance);
      if (found != null) {
        return found;
      }

      // find in parent scope if exists
      final var instanceParentKey = instance.getParentKey();
      instance = elementInstanceState.getInstance(instanceParentKey);

    } while (instance != null && instance.isActive());

    return null;
  }

  private CatchEventTuple findCatchEventInScope(
      final DirectBuffer errorCode,
      final ExecutableWorkflow workflow,
      final ElementInstance instance) {

    final var elementId = instance.getValue().getElementIdBuffer();
    final var activity = workflow.getElementById(elementId, ExecutableActivity.class);

    for (final ExecutableCatchEvent catchEvent : activity.getEvents()) {
      if (hasErrorCode(catchEvent, errorCode)) {

        catchEventTuple.instance = instance;
        catchEventTuple.catchEvent = catchEvent;
        return catchEventTuple;
      }
    }

    return null;
  }

  private boolean hasErrorCode(
      final ExecutableCatchEvent catchEvent, final DirectBuffer errorCode) {
    return catchEvent.isError() && catchEvent.getError().getErrorCode().equals(errorCode);
  }

  private void raiseIncident(
      final long key, final JobRecord job, final TypedStreamWriter streamWriter) {

    final DirectBuffer jobErrorMessage = job.getErrorMessageBuffer();
    DirectBuffer incidentErrorMessage =
        wrapString(
            String.format(
                "An error was thrown with the code '%s' but not caught.", job.getErrorCode()));
    if (jobErrorMessage.capacity() > 0) {
      incidentErrorMessage = jobErrorMessage;
    }

    incidentEvent.reset();
    incidentEvent
        .setErrorType(ErrorType.JOB_NO_RETRIES)
        .setErrorMessage(incidentErrorMessage)
        .setBpmnProcessId(job.getBpmnProcessIdBuffer())
        .setWorkflowKey(job.getWorkflowKey())
        .setWorkflowInstanceKey(job.getWorkflowInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey());

    streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentEvent);
  }

  private static class CatchEventTuple {
    private ExecutableCatchEvent catchEvent;
    private ElementInstance instance;
  }
}
