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
import io.zeebe.engine.state.instance.JobState.State;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

public final class ServiceTaskElementTerminatingHandler<T extends ExecutableServiceTask>
    extends ActivityElementTerminatingHandler<T> {
  private final IncidentState incidentState;
  private final JobState jobState;

  public ServiceTaskElementTerminatingHandler(
      final IncidentState incidentState,
      final CatchEventSubscriber catchEventSubscriber,
      final JobState jobState) {
    super(catchEventSubscriber);
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ElementInstance elementInstance = context.getElementInstance();
    final long jobKey = elementInstance.getJobKey();
    if (jobKey > 0) {
      final JobState.State state = jobState.getState(jobKey);

      if (state == State.NOT_FOUND) {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.warn(
            "Expected to find job with key {}, but no job found", jobKey);

      } else if (state == State.ACTIVATABLE || state == State.ACTIVATED || state == State.FAILED) {
        final JobRecord job = jobState.getJob(jobKey);
        context.getCommandWriter().appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
      }

      resolveExistingJobIncident(jobKey, context);
    }

    return true;
  }

  private void resolveExistingJobIncident(final long jobKey, final BpmnStepContext<T> context) {
    final long jobIncidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = jobIncidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(jobIncidentKey);
      context.getOutput().appendResolvedIncidentEvent(jobIncidentKey, incidentRecord);
    }
  }
}
