/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.servicetask;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class ServiceTaskElementTerminatingHandler<T extends ExecutableServiceTask>
    extends ActivityElementTerminatingHandler<T> {
  private final IncidentState incidentState;
  private final JobState jobState;

  public ServiceTaskElementTerminatingHandler(
      IncidentState incidentState, CatchEventSubscriber catchEventSubscriber, JobState jobState) {
    super(catchEventSubscriber);
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  public ServiceTaskElementTerminatingHandler(
      WorkflowInstanceIntent nextState,
      IncidentState incidentState,
      CatchEventSubscriber catchEventSubscriber,
      JobState jobState) {
    super(nextState, catchEventSubscriber);
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ElementInstance elementInstance = context.getElementInstance();
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      final JobRecord job = jobState.getJob(jobKey);

      if (job != null) {
        context.getCommandWriter().appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
      } else {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.warn(
            "Expected to find job with key {}, but no job found", jobKey);
      }

      resolveExistingJobIncident(jobKey, context);
    }

    return true;
  }

  private void resolveExistingJobIncident(long jobKey, BpmnStepContext<T> context) {
    final long jobIncidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = jobIncidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(jobIncidentKey);
      context.getOutput().appendResolvedIncidentEvent(jobIncidentKey, incidentRecord);
    }
  }
}
