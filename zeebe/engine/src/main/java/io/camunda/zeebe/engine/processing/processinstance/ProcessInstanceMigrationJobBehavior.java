/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.state.immutable.IncidentState.MISSING_INCIDENT;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationMigrateProcessor.SafetyCheckFailedException;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ProcessInstanceMigrationJobBehavior {

  private final BpmnBehaviors bpmnBehaviors;
  private final StateWriter stateWriter;
  private final JobState jobState;
  private final IncidentState incidentState;

  public ProcessInstanceMigrationJobBehavior(
      final BpmnBehaviors bpmnBehaviors,
      final StateWriter stateWriter,
      final JobState jobState,
      final IncidentState incidentState) {
    this.bpmnBehaviors = bpmnBehaviors;
    this.stateWriter = stateWriter;
    this.jobState = jobState;
    this.incidentState = incidentState;
  }

  public void migrateOrConvertJob(
      final ElementInstance elementInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final boolean isUserTaskConversion,
      final long processInstanceKey,
      final String targetElementId,
      final ProcessInstanceRecord updatedElementInstanceRecord) {
    if (isUserTaskConversion) {
      final ProcessInstanceMigrationUserTaskBehavior migrationUserTaskBehavior =
          new ProcessInstanceMigrationUserTaskBehavior(
              processInstanceKey,
              elementInstance,
              sourceProcessDefinition,
              targetProcessDefinition,
              bpmnBehaviors,
              targetElementId,
              updatedElementInstanceRecord,
              stateWriter,
              jobState);
      migrationUserTaskBehavior.tryMigrateJobWorkerToCamundaUserTask();
      // TODO: there may be a bug related to migrating incidents for job-based user tasks to Camunda
      // User Tasks. To write tests that cover this behaviour.
    } else {
      migrateJob(elementInstance, targetProcessDefinition, processInstanceKey, targetElementId);

      final var jobIncidentKey = incidentState.getJobIncidentKey(elementInstance.getJobKey());
      if (jobIncidentKey != MISSING_INCIDENT) {
        appendIncidentMigratedEvent(
            jobIncidentKey, targetProcessDefinition, targetElementId, updatedElementInstanceRecord);
      }
    }
  }

  private void migrateJob(
      final ElementInstance elementInstance,
      final DeployedProcess targetProcessDefinition,
      final long processInstanceKey,
      final String targetElementId) {
    if (elementInstance.getJobKey() > 0) {
      final var job = jobState.getJob(elementInstance.getJobKey());
      if (job == null) {
        throw new SafetyCheckFailedException(
            String.format(
                """
                Expected to migrate a job for process instance with key '%d', \
                but could not find job with key '%d'. \
                Please report this as a bug""",
                processInstanceKey, elementInstance.getJobKey()));
      }
      stateWriter.appendFollowUpEvent(
          elementInstance.getJobKey(),
          JobIntent.MIGRATED,
          job.setProcessDefinitionKey(targetProcessDefinition.getKey())
              .setProcessDefinitionVersion(targetProcessDefinition.getVersion())
              .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
              .setElementId(targetElementId));
    }
  }

  public void appendIncidentMigratedEvent(
      final long incidentKey,
      final DeployedProcess targetProcessDefinition,
      final String targetElementId,
      final ProcessInstanceRecord elementInstanceRecord) {
    final var incidentRecord = incidentState.getIncidentRecord(incidentKey);
    if (incidentRecord == null) {
      throw new SafetyCheckFailedException(
          String.format(
              """
              Expected to migrate a user task for process instance with key '%d', \
              but could not find incident with key '%d'. \
              Please report this as a bug""",
              elementInstanceRecord.getProcessInstanceKey(), incidentKey));
    }
    stateWriter.appendFollowUpEvent(
        incidentKey,
        IncidentIntent.MIGRATED,
        incidentRecord
            .setProcessDefinitionKey(targetProcessDefinition.getKey())
            .setBpmnProcessId(targetProcessDefinition.getBpmnProcessId())
            .setElementId(BufferUtil.wrapString(targetElementId))
            .setElementInstancePath(elementInstanceRecord.getElementInstancePath())
            .setProcessDefinitionPath(elementInstanceRecord.getProcessDefinitionPath())
            .setCallingElementPath(elementInstanceRecord.getCallingElementPath()));
  }
}
