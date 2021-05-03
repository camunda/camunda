/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.variable.VariableBehavior;
import io.zeebe.engine.state.deployment.DeployedProcess;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class BpmnStateBehavior {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variablesState;
  private final JobState jobState;
  private final ProcessState processState;
  private final VariableBehavior variableBehavior;

  public BpmnStateBehavior(
      final MutableZeebeState zeebeState, final VariableBehavior variableBehavior) {
    this.variableBehavior = variableBehavior;

    processState = zeebeState.getProcessState();
    elementInstanceState = zeebeState.getElementInstanceState();
    eventScopeInstanceState = zeebeState.getEventScopeInstanceState();
    variablesState = zeebeState.getVariableState();
    jobState = zeebeState.getJobState();
  }

  public ElementInstance getElementInstance(final BpmnElementContext context) {
    return getElementInstance(context.getElementInstanceKey());
  }

  public ElementInstance getElementInstance(final long elementInstanceKey) {
    return elementInstanceState.getInstance(elementInstanceKey);
  }

  public void updateElementInstance(final ElementInstance elementInstance) {
    elementInstanceState.updateInstance(elementInstance);
  }

  public void updateElementInstance(
      final BpmnElementContext context, final Consumer<ElementInstance> modifier) {
    elementInstanceState.updateInstance(context.getElementInstanceKey(), modifier);
  }

  public void updateFlowScopeInstance(
      final BpmnElementContext context, final Consumer<ElementInstance> modifier) {
    elementInstanceState.updateInstance(context.getFlowScopeKey(), modifier);
  }

  public void updateElementInstance(
      final long elementInstanceKey, final Consumer<ElementInstance> modifier) {
    elementInstanceState.updateInstance(elementInstanceKey, modifier);
  }

  public JobState getJobState() {
    return jobState;
  }

  // used by canceling, since we don't care about active sequence flows
  public boolean canBeTerminated(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);

    if (flowScopeInstance == null) {
      return false;
    }

    final long activePaths = flowScopeInstance.getNumberOfActiveElementInstances();
    if (activePaths < 0) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return hasActivePaths(context, activePaths);
  }

  public boolean canBeCompleted(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);

    if (flowScopeInstance == null) {
      return false;
    }

    final long activePaths =
        flowScopeInstance.getNumberOfActiveElementInstances()
            + flowScopeInstance.getActiveSequenceFlows();
    if (activePaths < 0) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return hasActivePaths(context, activePaths);
  }

  private boolean hasActivePaths(final BpmnElementContext context, final long activePaths) {
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      return activePaths == 1;
    } else {
      // todo (#6202): change the name of this method to `wasLastActiveExecutionPathInScope`
      // previously, the last active token was decreased after this method was called,
      // this made sure the token does not drop to 0 before the flowscope is set to completing.
      // However, the token must now be consumed when the ELEMENT_COMPLETED is written (by the event
      // applier). So either the number of active paths in the flowscope have to be already
      // decreased or the container scope must be completed before the child element is completed.
      // The only reasonable choice is to decrease the active paths before the flowscope is
      // completed. As a result, this method has changed it's semantics.
      return activePaths == 0;
    }
  }

  public ElementInstance getFlowScopeInstance(final BpmnElementContext context) {
    return elementInstanceState.getInstance(context.getFlowScopeKey());
  }

  public void removeElementInstance(final BpmnElementContext context) {
    eventScopeInstanceState.deleteInstance(context.getElementInstanceKey());
    elementInstanceState.removeInstance(context.getElementInstanceKey());
  }

  public List<BpmnElementContext> getChildInstances(final BpmnElementContext context) {
    return elementInstanceState.getChildren(context.getElementInstanceKey()).stream()
        .map(
            childInstance ->
                context.copy(
                    childInstance.getKey(), childInstance.getValue(), childInstance.getState()))
        .collect(Collectors.toList());
  }

  public void createElementInstanceInFlowScope(
      final BpmnElementContext context,
      final long elementInstanceKey,
      final ProcessInstanceRecord record) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);
    elementInstanceState.newInstance(
        flowScopeInstance, elementInstanceKey, record, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  public ElementInstance createElementInstance(
      final long childInstanceKey, final ProcessInstanceRecord childRecord) {
    return elementInstanceState.newInstance(
        childInstanceKey, childRecord, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  public BpmnElementContext getFlowScopeContext(final BpmnElementContext context) {
    final var flowScope = getFlowScopeInstance(context);
    return context.copy(flowScope.getKey(), flowScope.getValue(), flowScope.getState());
  }

  public BpmnElementContext getParentElementInstanceContext(final BpmnElementContext context) {
    final var parentElementInstance =
        elementInstanceState.getInstance(context.getParentElementInstanceKey());
    return context.copy(
        parentElementInstance.getKey(),
        parentElementInstance.getValue(),
        parentElementInstance.getState());
  }

  public Optional<DeployedProcess> getProcess(final long processDefinitionKey) {
    return Optional.ofNullable(processState.getProcessByKey(processDefinitionKey));
  }

  public Optional<DeployedProcess> getLatestProcessVersion(final DirectBuffer processId) {
    final var process = processState.getLatestProcessVersionByProcessId(processId);
    return Optional.ofNullable(process);
  }

  public Optional<ElementInstance> getCalledChildInstance(final BpmnElementContext context) {
    final var elementInstance = getElementInstance(context);
    final var calledChildInstanceKey = elementInstance.getCalledChildInstanceKey();
    return Optional.ofNullable(elementInstanceState.getInstance(calledChildInstanceKey));
  }

  public DirectBuffer getLocalVariable(
      final BpmnElementContext context, final DirectBuffer variableName) {
    return variablesState.getVariableLocal(context.getElementInstanceKey(), variableName);
  }

  public void setLocalVariable(
      final BpmnElementContext context,
      final DirectBuffer variableName,
      final DirectBuffer variableValue) {
    setLocalVariable(context, variableName, variableValue, 0, variableValue.capacity());
  }

  public void setLocalVariable(
      final BpmnElementContext context,
      final DirectBuffer variableName,
      final DirectBuffer variableValue,
      final int valueOffset,
      final int valueLength) {
    variableBehavior.setLocalVariable(
        context.getElementInstanceKey(),
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        variableName,
        variableValue,
        valueOffset,
        valueLength);
  }

  public void propagateVariable(final BpmnElementContext context, final DirectBuffer variableName) {

    final var sourceScope = context.getElementInstanceKey();
    final var targetScope = context.getFlowScopeKey();

    final var variablesAsDocument =
        variablesState.getVariablesAsDocument(sourceScope, List.of(variableName));

    variableBehavior.mergeDocument(
        targetScope,
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        variablesAsDocument);
  }

  public void copyVariablesToProcessInstance(
      final long sourceScopeKey,
      final long targetProcessInstanceKey,
      final DeployedProcess targetProcess) {
    final var variables = variablesState.getVariablesAsDocument(sourceScopeKey);
    variableBehavior.mergeDocument(
        targetProcessInstanceKey, targetProcess.getKey(), targetProcessInstanceKey, variables);
  }

  public void propagateTemporaryVariables(
      final BpmnElementContext sourceContext, final BpmnElementContext targetContext) {
    final var variables =
        variablesState.getVariablesAsDocument(sourceContext.getElementInstanceKey());
    variablesState.setTemporaryVariables(targetContext.getElementInstanceKey(), variables);
  }

  public boolean isInterrupted(final BpmnElementContext flowScopeContext) {
    final var flowScopeInstance =
        elementInstanceState.getInstance(flowScopeContext.getElementInstanceKey());
    return flowScopeInstance.getNumberOfActiveElementInstances() == 0
        && flowScopeInstance.isInterrupted()
        && flowScopeInstance.isActive();
  }
}
