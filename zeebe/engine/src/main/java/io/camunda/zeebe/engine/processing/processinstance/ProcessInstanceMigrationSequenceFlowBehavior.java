/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.requireNoConcurrentCommandForGateway;
import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.requireValidGatewayMapping;
import static io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationPreconditions.requireValidTargetIncomingFlowCount;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class ProcessInstanceMigrationSequenceFlowBehavior {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ElementInstanceState elementInstanceState;

  public ProcessInstanceMigrationSequenceFlowBehavior(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ElementInstanceState elementInstanceState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.elementInstanceState = elementInstanceState;
  }

  public void migrateSequenceFlows(
      final ElementInstance elementInstance,
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ProcessInstanceRecord elementInstanceRecord,
      final ProcessInstanceRecord updatedElementInstanceRecord) {
    final Set<ExecutableSequenceFlow> sequenceFlows =
        getSequenceFlowsToMigrate(
            sourceProcessDefinition,
            targetProcessDefinition,
            sourceElementIdToTargetElementId,
            elementInstance);

    sequenceFlows.forEach(
        sequenceFlow -> {
          // use the original element instance record to fill existing sequence flow data
          deleteTakenSequenceFlow(elementInstanceRecord, sequenceFlow, elementInstance.getKey());

          final var targetSequenceFlowId =
              getTargetSequenceFlowId(sourceElementIdToTargetElementId, sequenceFlow);
          // use updated element instance record to fill new sequence flow data
          takeNewSequenceFlow(
              updatedElementInstanceRecord,
              sequenceFlow,
              elementInstance.getKey(),
              targetSequenceFlowId);
        });
  }

  private Set<ExecutableSequenceFlow> getSequenceFlowsToMigrate(
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final Map<String, String> sourceElementIdToTargetElementId,
      final ElementInstance elementInstance) {
    final long elementInstanceKey = elementInstance.getKey();
    final long processInstanceKey = elementInstance.getValue().getProcessInstanceKey();

    final List<ActiveSequenceFlow> activeSequenceFlows = new ArrayList<>();
    elementInstanceState.visitTakenSequenceFlows(
        elementInstanceKey,
        (scopeKey, gatewayElementId, sequenceFlowId, number) -> {
          final var sequenceFlow =
              sourceProcessDefinition
                  .getProcess()
                  .getElementById(sequenceFlowId, ExecutableSequenceFlow.class);
          activeSequenceFlows.add(new ActiveSequenceFlow(sequenceFlow, sequenceFlow.getTarget()));
        });

    return activeSequenceFlows.stream()
        .filter(
            sequenceFlow -> {
              final BpmnElementType elementType = sequenceFlow.target().getElementType();
              return elementType == BpmnElementType.PARALLEL_GATEWAY
                  || elementType == BpmnElementType.INCLUSIVE_GATEWAY;
            })
        .map(
            activeSequenceFlow -> {
              final ExecutableSequenceFlow activeFlow = activeSequenceFlow.sequenceFlow();
              final ExecutableFlowNode sourceGateway = activeSequenceFlow.target;
              requireNoConcurrentCommandForGateway(
                  elementInstanceState, sourceGateway, elementInstanceKey, processInstanceKey);

              final String targetGatewayId =
                  sourceElementIdToTargetElementId.get(
                      BufferUtil.bufferAsString(sourceGateway.getId()));
              requireValidGatewayMapping(
                  sourceGateway, targetGatewayId, targetProcessDefinition, processInstanceKey);

              final ExecutableFlowNode targetGateway =
                  targetProcessDefinition
                      .getProcess()
                      .getElementById(targetGatewayId, ExecutableFlowNode.class);
              requireValidTargetIncomingFlowCount(sourceGateway, targetGateway, processInstanceKey);

              return activeFlow;
            })
        .collect(Collectors.toSet());
  }

  private void deleteTakenSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey) {
    handleSequenceFlow(
        elementInstanceRecord,
        sequenceFlow,
        elementInstanceKey,
        sequenceFlow.getId(),
        ProcessInstanceIntent.SEQUENCE_FLOW_DELETED);
  }

  private void takeNewSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey,
      final DirectBuffer sequenceFlowId) {
    handleSequenceFlow(
        elementInstanceRecord,
        sequenceFlow,
        elementInstanceKey,
        sequenceFlowId,
        ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  private void handleSequenceFlow(
      final ProcessInstanceRecord elementInstanceRecord,
      final ExecutableSequenceFlow sequenceFlow,
      final long elementInstanceKey,
      final DirectBuffer sequenceFlowId,
      final ProcessInstanceIntent intent) {
    final var sequenceFlowRecord = new ProcessInstanceRecord();
    sequenceFlowRecord.copyFrom(elementInstanceRecord);
    sequenceFlowRecord
        .setElementId(sequenceFlowId)
        .setBpmnElementType(sequenceFlow.getElementType())
        .setBpmnEventType(sequenceFlow.getEventType())
        .setFlowScopeKey(elementInstanceKey)
        .resetElementInstancePath()
        .resetCallingElementPath()
        .resetProcessDefinitionPath();

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), intent, sequenceFlowRecord);
  }

  private static DirectBuffer getTargetSequenceFlowId(
      final Map<String, String> sourceElementIdToTargetElementId,
      final ExecutableSequenceFlow sequenceFlow) {
    final String sourceSequenceFlowId = BufferUtil.bufferAsString(sequenceFlow.getId());
    final String targetSequenceFlowId = sourceElementIdToTargetElementId.get(sourceSequenceFlowId);

    return BufferUtil.wrapString(targetSequenceFlowId);
  }

  record ActiveSequenceFlow(ExecutableSequenceFlow sequenceFlow, ExecutableFlowNode target) {}
}
