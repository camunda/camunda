/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.usertask.UserTaskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.agrona.DirectBuffer;

public final class JobCompleteProcessor implements CommandProcessor<JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update retries for job with key '%d', but no such job was found";

  private final JobState jobState;

  private final UserTaskState userTaskState;

  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard defaultProcessor;
  private final JobMetrics jobMetrics;
  private final EventHandle eventHandle;

  public JobCompleteProcessor(
      final ProcessingState state, final JobMetrics jobMetrics, final EventHandle eventHandle) {
    jobState = state.getJobState();
    userTaskState = state.getUserTaskState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard("complete", jobState, this::acceptCommand);
    this.jobMetrics = jobMetrics;
    this.eventHandle = eventHandle;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final JobRecord value) {

    final var serviceTaskKey = value.getElementInstanceKey();

    final ElementInstance serviceTask = elementInstanceState.getInstance(serviceTaskKey);

    if (value.getType().startsWith("_userTaskListener")) {
      // this is a user task listener job

      // find the user task
      final String userTaskKeyAsString = value.getCustomHeaders().get("userTaskKey");
      final Long userTaskKey = Long.valueOf(userTaskKeyAsString);

      final UserTaskRecord userTask = userTaskState.get(userTaskKey);

      // validate user task

      // update user task
      final String assignee = value.getCustomHeaders().get(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME);
      if (assignee != null) {
        userTask.setAssignee(assignee);
      }
      // write event to store the change in state (e.g. user task UPDATED)

      // check if there are more listeners

      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTask);

      // transition the element instance
      stateWriter.appendFollowUpEvent(
          value.getElementInstanceKey(),
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          serviceTask.getValue());

      // the end
      return;
    }

    if (serviceTask != null) {
      final long scopeKey = serviceTask.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        eventHandle.triggeringProcessEvent(value);
        commandWriter.appendFollowUpCommand(
            serviceTaskKey, ProcessInstanceIntent.COMPLETE_ELEMENT, serviceTask.getValue());
      }
    }
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {

    final long jobKey = command.getKey();

    final JobRecord job = jobState.getJob(jobKey, command.getAuthorizations());
    if (job == null) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));
      return;
    }

    job.setVariables(command.getValue().getVariablesBuffer());

    // just update the custom headers - all fine
    final DirectBuffer modifiedCustomHeaders = command.getValue().getCustomHeadersBuffer();
    job.setCustomHeaders(modifiedCustomHeaders);

    commandControl.accept(JobIntent.COMPLETED, job);
    jobMetrics.jobCompleted(job.getType());
  }
}
