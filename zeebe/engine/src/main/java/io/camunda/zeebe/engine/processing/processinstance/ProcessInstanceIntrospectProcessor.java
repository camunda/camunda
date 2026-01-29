/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceIntrospectActionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceIntrospectRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntrospectIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class ProcessInstanceIntrospectProcessor
    implements TypedRecordProcessor<ProcessInstanceIntrospectRecord> {

  private final TypedEventWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;
  private final ProcessingState processingState;

  public ProcessInstanceIntrospectProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.processingState = processingState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceIntrospectRecord> record) {
    final var key = keyGenerator.nextKey();

    // TODO enrich the record with actual introspection data
    final var processInstanceKey = record.getValue().getProcessInstanceKey();
    final var processInstance =
        processingState.getElementInstanceState().getInstance(processInstanceKey);
    // TODO fix tenant
    final var processDef =
        processingState
            .getProcessState()
            .getProcessByKeyAndTenant(
                processInstance.getValue().getProcessDefinitionKey(),
                TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final var children = processingState.getElementInstanceState().getChildren(processInstanceKey);
    children.stream()
        .map(child -> getAction(child, processDef))
        .forEach(
            action ->
                record
                    .getValue()
                    .addAction(
                        new ProcessInstanceIntrospectActionRecord()
                            .setAction(action.type.name())
                            .setElementInstanceKey(action.elementInstanceKey)
                            .setParameters(action.parameters)));

    getParallelGatewayActions(processInstance, processDef)
        .forEach(
            action ->
                record
                    .getValue()
                    .addAction(
                        new ProcessInstanceIntrospectActionRecord()
                            .setAction(action.type.name())
                            .setElementInstanceKey(action.elementInstanceKey)
                            .setParameters(action.parameters)));

    stateWriter.appendFollowUpEvent(
        key, ProcessInstanceIntrospectIntent.INTROSPECTED, record.getValue());
    responseWriter.writeEventOnCommand(
        key, ProcessInstanceIntrospectIntent.INTROSPECTED, record.getValue(), record);
  }

  private Action getAction(
      final ElementInstance elementInstance, final DeployedProcess deployedProcess) {
    return switch (elementInstance.getValue().getBpmnElementType()) {
      case USER_TASK -> getUserTaskAction(elementInstance, deployedProcess);
      case SERVICE_TASK -> getServiceTaskAction(elementInstance);
      default -> new Action(ActionType.UNKNOWN, -1L, Map.of());
    };
  }

  private List<Action> getParallelGatewayActions(
      final ElementInstance processInstance, final DeployedProcess deployedProcess) {
    final var takenSequenceFlows = new HashSet<DirectBuffer>();
    processingState
        .getElementInstanceState()
        .visitTakenSequenceFlows(
            processInstance.getKey(),
            (flowScopeKey, gatewayElementId, sequenceFlowId, number) -> {
              takenSequenceFlows.add(sequenceFlowId);
            });

    final var takenParallelGatewaySequenceFlows =
        takenSequenceFlows.stream()
            .map(
                flow ->
                    deployedProcess.getProcess().getElementById(flow, ExecutableSequenceFlow.class))
            .filter(flow -> flow.getTarget().getElementType() == BpmnElementType.PARALLEL_GATEWAY)
            .collect(
                Collectors.toMap(
                    flow -> BufferUtil.bufferAsString(flow.getTarget().getId()),
                    flow -> {
                      final var array = new ArrayList<String>();
                      array.add(BufferUtil.bufferAsString(flow.getId()));
                      return array;
                    },
                    (a, b) -> {
                      a.addAll(b);
                      return a;
                    }));

    final var actions = new ArrayList<Action>();
    takenParallelGatewaySequenceFlows.forEach(
        (gatewayId, takenFlows) -> {
          final var gateway =
              deployedProcess.getProcess().getElementById(gatewayId, ExecutableFlowNode.class);
          final var incomingFlows = gateway.getIncoming();
          final var missingSequenceFlows =
              incomingFlows.stream()
                  .map(flow -> BufferUtil.bufferAsString(flow.getId()))
                  .filter(flowId -> !takenFlows.contains(flowId))
                  .toList();
          if (!missingSequenceFlows.isEmpty()) {
            actions.add(
                new Action(
                    ActionType.PARALLEL_GATEWAY,
                    -1L,
                    Map.of("awaitingSequenceFlows", missingSequenceFlows.toString())));
          }
        });

    return actions;
  }

  private Action getServiceTaskAction(final ElementInstance elementInstance) {
    final var job = processingState.getJobState().getJob(elementInstance.getJobKey());
    return new Action(
        ActionType.JOB_WORKER, elementInstance.getKey(), Map.of("jobType", job.getType()));
  }

  private Action getUserTaskAction(
      final ElementInstance elementInstance, final DeployedProcess deployedProcess) {
    final var element =
        deployedProcess
            .getProcess()
            .getElementById(elementInstance.getValue().getElementId(), ExecutableUserTask.class);

    // Check if the element has a job type definition (indicating a job worker user task)
    // vs. being a native Camunda user task
    if (element != null && element.getJobWorkerProperties() != null) {
      final var job = processingState.getJobState().getJob(elementInstance.getJobKey());
      return new Action(
          ActionType.JOB_WORKER, elementInstance.getKey(), Map.of("jobType", job.getType()));
    }

    return new Action(ActionType.USER_TASK, elementInstance.getKey(), Map.of());
  }

  public record Action(ActionType type, Long elementInstanceKey, Map<String, String> parameters) {}

  enum ActionType {
    USER_TASK,
    JOB_WORKER,
    PARALLEL_GATEWAY,
    UNKNOWN;
  }
}
