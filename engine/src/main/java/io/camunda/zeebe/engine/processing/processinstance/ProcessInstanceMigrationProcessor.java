/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ProcessInstanceMigrationProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final StateWriter stateWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;

  public ProcessInstanceMigrationProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final JobState jobState,
      final BpmnBehaviors bpmnBehaviors) {
    stateWriter = writers.state();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.jobState = jobState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ElementInstance processInstance = elementInstanceState.getInstance(command.getKey());
    final DeployedProcess targetProcess =
        processState.getProcessByKeyAndTenant(
            command.getValue().getTargetProcessDefinitionKey(),
            TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    stateWriter.appendFollowUpEvent(
        command.getKey(),
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        processInstance
            .getValue()
            .setProcessDefinitionKey(targetProcess.getKey())
            .setBpmnProcessId(targetProcess.getBpmnProcessId())
            .setVersion(targetProcess.getVersion())
            .setElementId(targetProcess.getBpmnProcessId()));

    elementInstanceState.forEachChild(
        processInstance.getKey(),
        processInstance.getKey(),
        (key, instance) -> {
          stateWriter.appendFollowUpEvent(
              key,
              ProcessInstanceIntent.ELEMENT_MIGRATED,
              instance
                  .getValue()
                  .setProcessDefinitionKey(targetProcess.getKey())
                  .setBpmnProcessId(targetProcess.getBpmnProcessId())
                  .setVersion(targetProcess.getVersion())
              // .setElementId(targetProcess.getBpmnProcessId()) // todo id may change
              );

          final long jobKey = instance.getJobKey();
          if (jobKey > -1) {
            final JobRecord job = jobState.getJob(jobKey);
            stateWriter.appendFollowUpEvent(
                jobKey,
                JobIntent.MIGRATED,
                job.setProcessDefinitionKey(targetProcess.getKey())
                    .setBpmnProcessId(targetProcess.getBpmnProcessId())
                    .setProcessDefinitionVersion(targetProcess.getVersion())
                // .setElementId(targetProcess.getBpmnProcessId()) // todo id may change
                );
          }

          return true;
        });

    stateWriter.appendFollowUpEvent(
        command.getKey(), ProcessInstanceMigrationIntent.MIGRATED, command.getValue());
  }
}
