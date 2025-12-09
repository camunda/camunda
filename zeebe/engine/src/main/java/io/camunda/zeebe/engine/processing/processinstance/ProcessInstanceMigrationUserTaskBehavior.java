/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationMigrateProcessor.SafetyCheckFailedException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public class ProcessInstanceMigrationUserTaskBehavior {

  private final ElementInstance elementInstance;
  private final DeployedProcess sourceProcessDefinition;
  private final DeployedProcess targetProcessDefinition;
  private final String targetElementId;
  private final StateWriter stateWriter;
  private final JobState jobState;

  public ProcessInstanceMigrationUserTaskBehavior(
      final ElementInstance elementInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final StateWriter stateWriter,
      final JobState jobState) {
    this.elementInstance = elementInstance;
    this.sourceProcessDefinition = sourceProcessDefinition;
    this.targetProcessDefinition = targetProcessDefinition;
    this.targetElementId = targetElementId;
    this.stateWriter = stateWriter;
    this.jobState = jobState;
  }

  public void tryMigrateJobWorkerToCamundaUserTask(final long processInstanceKey) {
    final var jobKey = elementInstance.getJobKey();

    final var job = jobState.getJob(jobKey);
    if (job == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
                  Expected to migrate a job for process instance with key '%d', \
                  but could not find job with key '%d'. \
                  Please report this as a bug""",
              processInstanceKey, jobKey));
    }

    final String elementId = elementInstance.getValue().getElementId();
    final ExecutableJobWorkerElement sourceElement =
        sourceProcessDefinition
            .getProcess()
            .getElementById(elementId, ExecutableJobWorkerElement.class);
    final ExecutableUserTask targetElement =
        targetProcessDefinition
            .getProcess()
            .getElementById(targetElementId, ExecutableUserTask.class);

    // Cancel previous job worker job
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.CANCELED, job);
  }
}
