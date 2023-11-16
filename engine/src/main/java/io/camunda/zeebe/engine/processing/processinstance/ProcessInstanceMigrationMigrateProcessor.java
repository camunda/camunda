/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ProcessInstanceMigrationMigrateProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceMigrationMigrateProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceMigrationRecord> command) {
    final ProcessInstanceMigrationRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long targetProcessDefinitionKey = value.getTargetProcessDefinitionKey();

    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      // todo: we should reject the command explicitly
      throw new IllegalArgumentException(
          String.format(
              "Expected to migrate process instance with key '%d', but process instance not found",
              processInstanceKey));
    }

    final DeployedProcess processDefinition =
        processState.getProcessByKeyAndTenant(
            targetProcessDefinitionKey, processInstance.getValue().getTenantId());
    if (processDefinition == null) {
      // todo: we should reject the command explicitly
      throw new IllegalStateException(
          String.format(
              "Expected to migrate process instance with key '%d' to process definition with key '%d', but process definition not found",
              value.getProcessInstanceKey(), targetProcessDefinitionKey));
    }

    stateWriter.appendFollowUpEvent(
        processInstanceKey,
        ProcessInstanceIntent.ELEMENT_MIGRATED,
        processInstance
            .getValue()
            .setProcessDefinitionKey(processDefinition.getKey())
            .setBpmnProcessId(processDefinition.getBpmnProcessId())
            .setVersion(processDefinition.getVersion())
            .setElementId(processDefinition.getBpmnProcessId()));

    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceMigrationIntent.MIGRATED, value, command);
  }
}
