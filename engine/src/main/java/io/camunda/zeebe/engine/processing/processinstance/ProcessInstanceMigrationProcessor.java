/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ProcessInstanceMigrationProcessor
    implements TypedRecordProcessor<ProcessInstanceMigrationRecord> {

  private final StateWriter stateWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final JobState jobState;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final KeyGenerator keyGenerator;

  public ProcessInstanceMigrationProcessor(
      final Writers writers,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState,
      final JobState jobState,
      final BpmnBehaviors bpmnBehaviors,
      final KeyGenerator keyGenerator) {
    stateWriter = writers.state();
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.jobState = jobState;
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    this.keyGenerator = keyGenerator;
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
          final ExecutableFlowElement targetElement =
              processState.getFlowElement(
                  targetProcess.getKey(), TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                  instance.getValue().getElementIdBuffer(), ExecutableFlowElement.class);

          final ExecutableFlowElement sourceElement =
              processState.getFlowElement(
                  instance.getValue().getProcessDefinitionKey(),
                      TenantOwned.DEFAULT_TENANT_IDENTIFIER,
                  instance.getValue().getElementIdBuffer(), ExecutableFlowElement.class);

          long instanceFlowScopeKey = instance.getValue().getFlowScopeKey();

          final String sourceFlowScopeId =
              BufferUtil.bufferAsString(sourceElement.getFlowScope().getId());
          final String targetFlowScopeId =
              BufferUtil.bufferAsString(targetElement.getFlowScope().getId());
          if (!sourceFlowScopeId.equals(targetFlowScopeId)) {
            final long subProcessInstanceKey = keyGenerator.nextKey();
            final ProcessInstanceRecord subProcessInstanceRecord = new ProcessInstanceRecord();
            subProcessInstanceRecord
                .setBpmnElementType(targetElement.getFlowScope().getElementType())
                .setElementId(targetElement.getFlowScope().getId())
                .setBpmnProcessId(targetProcess.getBpmnProcessId())
                .setVersion(targetProcess.getVersion())
                .setProcessDefinitionKey(targetProcess.getKey())
                .setProcessInstanceKey(processInstance.getKey())
                .setFlowScopeKey(processInstance.getKey())
                .setBpmnEventType(targetElement.getFlowScope().getEventType())
                .setParentProcessInstanceKey(instance.getValue().getParentProcessInstanceKey())
                .setParentElementInstanceKey(instance.getValue().getParentElementInstanceKey())
                .setTenantId(instance.getValue().getTenantId());

            stateWriter.appendFollowUpEvent(
                subProcessInstanceKey,
                ProcessInstanceIntent.ELEMENT_ACTIVATING,
                subProcessInstanceRecord);

            stateWriter.appendFollowUpEvent(
                subProcessInstanceKey,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                subProcessInstanceRecord);

            instanceFlowScopeKey = subProcessInstanceKey;
          }

          stateWriter.appendFollowUpEvent(
              key,
              ProcessInstanceIntent.ELEMENT_MIGRATED,
              instance
                  .getValue()
                  .setProcessDefinitionKey(targetProcess.getKey())
                  .setBpmnProcessId(targetProcess.getBpmnProcessId())
                  .setVersion(targetProcess.getVersion())
                  .setFlowScopeKey(instanceFlowScopeKey)
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

          final var context = new BpmnElementContextImpl();
          context.init(
              key,
              instance
                  .getValue()
                  .setProcessDefinitionKey(targetProcess.getKey())
                  .setBpmnProcessId(targetProcess.getBpmnProcessId())
                  .setVersion(targetProcess.getVersion()),
              instance.getState());

          // eventSubscriptionBehavior.unsubscribeFromEvents(context);
          eventSubscriptionBehavior.subscribeToEvents(
              (ExecutableCatchEventSupplier) targetElement, context);

          return true;
        });

    stateWriter.appendFollowUpEvent(
        command.getKey(), ProcessInstanceMigrationIntent.MIGRATED, command.getValue());
  }
}
