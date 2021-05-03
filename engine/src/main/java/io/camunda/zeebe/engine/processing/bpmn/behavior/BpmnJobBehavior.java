/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;

public final class BpmnJobBehavior {

  private final JobRecord jobRecord = new JobRecord().setVariables(DocumentValue.EMPTY_DOCUMENT);

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final JobState jobState;

  public BpmnJobBehavior(
      final KeyGenerator keyGenerator, final JobState jobState, final Writers writers) {
    this.keyGenerator = keyGenerator;
    this.jobState = jobState;
    stateWriter = writers.state();
    commandWriter = writers.command();
  }

  public void createNewJob(
      final BpmnElementContext context,
      final ExecutableServiceTask serviceTask,
      final String jobType,
      final int retries) {

    jobRecord
        .setType(jobType)
        .setRetries(retries)
        .setCustomHeaders(serviceTask.getEncodedHeaders())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionVersion(context.getProcessVersion())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setElementId(serviceTask.getId())
        .setElementInstanceKey(context.getElementInstanceKey());

    final var jobKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CREATED, jobRecord);
  }

  public void cancelJob(final long jobKey) {
    final State state = jobState.getState(jobKey);

    if (state == State.ACTIVATABLE || state == State.ACTIVATED || state == State.FAILED) {
      final JobRecord job = jobState.getJob(jobKey);
      commandWriter.appendFollowUpCommand(jobKey, JobIntent.CANCEL, job);
    }
  }
}
