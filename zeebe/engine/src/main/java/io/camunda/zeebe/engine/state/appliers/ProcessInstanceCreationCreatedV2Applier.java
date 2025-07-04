/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.List;
import org.agrona.DirectBuffer;

final class ProcessInstanceCreationCreatedV2Applier
    implements TypedEventApplier<ProcessInstanceCreationIntent, ProcessInstanceCreationRecord> {

  private final ProcessState processState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableUsageMetricState usageMetricState;

  public ProcessInstanceCreationCreatedV2Applier(
      final MutableProcessState processState,
      final MutableElementInstanceState elementInstanceState,
      final MutableUsageMetricState usageMetricState) {
    this.processState = processState;
    this.elementInstanceState = elementInstanceState;
    this.usageMetricState = usageMetricState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceCreationRecord value) {
    if (value.hasStartInstructions()) {
      final var process =
          processState
              .getProcessByKeyAndTenant(value.getProcessDefinitionKey(), value.getTenantId())
              .getProcess();
      final ElementInstance processInstance =
          elementInstanceState.getInstance(value.getProcessInstanceKey());

      value.getStartInstructions().stream()
          .map(instruction -> process.getElementById(instruction.getElementId()))
          .filter(element -> element.getElementType().equals(BpmnElementType.PARALLEL_GATEWAY))
          .map(ExecutableFlowNode.class::cast)
          .forEach(
              element -> {
                final var parentElementId = element.getFlowScope().getId();
                final ElementInstance flowScope =
                    findParentFlowScope(processInstance, parentElementId);
                incrementNumberOfTakenSequenceFlows(element, flowScope);
              });
    }
    if (!value.getRuntimeInstructions().isEmpty()) {
      elementInstanceState.addRuntimeInstructions(
          value.getProcessInstanceKey(), value.getRuntimeInstructions());
    }

    incrementUsageMetric(value);
  }

  private void incrementUsageMetric(final ProcessInstanceCreationRecord value) {
    usageMetricState.recordRPIMetric(value.getTenantId());
  }

  /**
   * Traverses the element instances to find one that matches the parent element id. If this is the
   * process instance, it is returned immediately. This will work because we will always activate
   * the flow scope of a start instruction once.
   *
   * @param processInstance the highest element instance of a process
   * @param targetElementId the id we are looking for
   * @return the element instance which matches the targetElementId
   */
  private ElementInstance findParentFlowScope(
      final ElementInstance processInstance, final DirectBuffer targetElementId) {
    if (processInstance.getValue().getElementIdBuffer().equals(targetElementId)) {
      return processInstance;
    }
    return findFlowScopeInChildren(processInstance, targetElementId);
  }

  private ElementInstance findFlowScopeInChildren(
      final ElementInstance processInstance, final DirectBuffer targetElementId) {
    ElementInstance found = null;
    final List<ElementInstance> children =
        elementInstanceState.getChildren(processInstance.getKey());

    for (final ElementInstance childInstance : children) {
      if (childInstance.getValue().getElementIdBuffer().equals(targetElementId)) {
        found = childInstance;
        break;
      } else {
        found = findFlowScopeInChildren(childInstance, targetElementId);
        if (found != null) {
          break;
        }
      }
    }

    return found;
  }

  private void incrementNumberOfTakenSequenceFlows(
      final ExecutableFlowNode element, final ElementInstance flowScope) {
    element
        .getIncoming()
        .forEach(
            incoming ->
                elementInstanceState.incrementNumberOfTakenSequenceFlows(
                    flowScope.getKey(), element.getId(), incoming.getId()));
  }
}
