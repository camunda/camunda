/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableInclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Collections;
import java.util.List;

/**
 * Applies state changes for {@code ProcessInstance:Element_Activating}.
 *
 * <p>V4 extends V3 by pinning the current global execution listener configuration to the element
 * instance, so that config changes during execution do not affect in-flight elements.
 */
final class ProcessInstanceElementActivatingV4Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableGlobalListenersState globalListenersState;

  public ProcessInstanceElementActivatingV4Applier(
      final MutableElementInstanceState elementInstanceState,
      final ProcessState processState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableGlobalListenersState globalListenersState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.globalListenersState = globalListenersState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {

    createEventScope(elementInstanceKey, value);
    final var numberOfTakenSequenceFlows =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            value.getFlowScopeKey(), value.getElementIdBuffer());
    cleanupSequenceFlowsTaken(value);

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());
    final var elementInstance =
        elementInstanceState.newInstance(
            flowScopeInstance, elementInstanceKey, value, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    if (value.getBpmnElementType() == BpmnElementType.PROCESS
        && !value.hasParentProcessInstance()) {
      insertBusinessIdIndex(value);
    }

    pinGlobalExecutionListenersConfig(elementInstanceKey);

    if (flowScopeInstance == null) {
      applyRootProcessState(elementInstance, value);
      return;
    }

    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();
    final var currentElementType = value.getBpmnElementType();

    decrementActiveSequenceFlow(
        value,
        flowScopeInstance,
        flowScopeElementType,
        currentElementType,
        numberOfTakenSequenceFlows);

    if (currentElementType == BpmnElementType.START_EVENT
        && flowScopeElementType == BpmnElementType.EVENT_SUB_PROCESS) {
      final EventTrigger flowScopeEventTrigger =
          eventScopeInstanceState.peekEventTrigger(flowScopeInstance.getParentKey());
      moveVariablesToNewEventScope(
          flowScopeEventTrigger, flowScopeInstance.getParentKey(), elementInstanceKey);
    }

    manageMultiInstance(elementInstanceKey, flowScopeInstance, flowScopeElementType);
  }

  private void pinGlobalExecutionListenersConfig(final long elementInstanceKey) {
    final var currentConfig = globalListenersState.getCurrentConfig();
    if (currentConfig == null) {
      return;
    }
    final var currentConfigKey = currentConfig.getGlobalListenerBatchKey();
    globalListenersState.storeConfigurationVersion(currentConfig);
    globalListenersState.pinConfiguration(currentConfigKey, elementInstanceKey);
    elementInstanceState.updateInstance(
        elementInstanceKey, instance -> instance.setExecutionListenersConfigKey(currentConfigKey));
  }

  private void cleanupSequenceFlowsTaken(final ProcessInstanceRecord value) {
    if (value.getBpmnElementType() == BpmnElementType.PARALLEL_GATEWAY
        || value.getBpmnElementType() == BpmnElementType.INCLUSIVE_GATEWAY) {

      final var gateway =
          processState.getFlowElement(
              value.getProcessDefinitionKey(),
              value.getTenantId(),
              value.getElementIdBuffer(),
              ExecutableFlowNode.class);
      elementInstanceState.decrementNumberOfTakenSequenceFlows(
          value.getFlowScopeKey(), gateway.getId());
    }
  }

  private void moveVariablesToNewEventScope(
      final EventTrigger eventTrigger, final long oldEventScopeKey, final long newEventScopeKey) {
    if (eventTrigger != null) {
      eventScopeInstanceState.triggerEvent(
          newEventScopeKey,
          eventTrigger.getEventKey(),
          eventTrigger.getElementId(),
          eventTrigger.getVariables(),
          eventTrigger.getProcessInstanceKey());
      eventScopeInstanceState.deleteTrigger(oldEventScopeKey, eventTrigger.getEventKey());
    }
  }

  private void applyRootProcessState(
      final ElementInstance elementInstance, final ProcessInstanceRecord value) {
    final var parentElementInstance =
        elementInstanceState.getInstance(value.getParentElementInstanceKey());
    if (parentElementInstance != null) {
      final var parentElementType = parentElementInstance.getValue().getBpmnElementType();
      if (parentElementType == BpmnElementType.CALL_ACTIVITY) {
        parentElementInstance.setCalledChildInstanceKey(elementInstance.getKey());
        elementInstanceState.updateInstance(parentElementInstance);

        final var parentProcessInstance =
            elementInstanceState.getInstance(
                elementInstance.getValue().getParentProcessInstanceKey());
        elementInstance.setProcessDepth(parentProcessInstance.getProcessDepth() + 1);
        elementInstanceState.updateInstance(elementInstance);
      }
    }
  }

  private void decrementActiveSequenceFlow(
      final ProcessInstanceRecord value,
      final ElementInstance flowScopeInstance,
      final BpmnElementType flowScopeElementType,
      final BpmnElementType currentElementType,
      final int numberOfTakenSequenceFlows) {
    processState
        .getFlowElement(
            value.getProcessDefinitionKey(),
            value.getTenantId(),
            value.getElementIdBuffer(),
            ExecutableFlowNode.class)
        .getIncoming()
        .stream()
        .map(ExecutableSequenceFlow::getId)
        .forEach(flowScopeInstance::removeActiveSequenceFlowId);
    elementInstanceState.updateInstance(flowScopeInstance);

    switch (currentElementType) {
      case START_EVENT:
      case BOUNDARY_EVENT:
        break;
      case INTERMEDIATE_CATCH_EVENT:
        decrementIntermediateCatchEventSequenceFlow(value, flowScopeInstance);
        break;
      case PARALLEL_GATEWAY:
        decrementParallelGatewaySequenceFlow(value, flowScopeInstance);
        break;
      case INCLUSIVE_GATEWAY:
        decrementInclusiveGatewaySequenceFlow(flowScopeInstance, numberOfTakenSequenceFlows);
        break;
      case EVENT_SUB_PROCESS:
        decrementEventSubProcessSequenceFlow(value, flowScopeInstance);
        break;
      default:
        if (flowScopeElementType != BpmnElementType.MULTI_INSTANCE_BODY) {
          flowScopeInstance.decrementActiveSequenceFlows();
          elementInstanceState.updateInstance(flowScopeInstance);
        }
        break;
    }
  }

  private void decrementIntermediateCatchEventSequenceFlow(
      final ProcessInstanceRecord value, final ElementInstance flowScopeInstance) {
    final var executableCatchEventElement =
        processState.getFlowElement(
            value.getProcessDefinitionKey(),
            value.getTenantId(),
            value.getElementIdBuffer(),
            ExecutableCatchEventElement.class);

    final List<ExecutableSequenceFlow> incoming = executableCatchEventElement.getIncoming();
    if (!incoming.isEmpty()) {
      final var incomingSequenceFlow = executableCatchEventElement.getIncoming().get(0);
      final var previousElement = incomingSequenceFlow.getSource();
      if (previousElement.getElementType() != BpmnElementType.EVENT_BASED_GATEWAY) {
        flowScopeInstance.decrementActiveSequenceFlows();
        elementInstanceState.updateInstance(flowScopeInstance);
      }
    }
  }

  private void decrementParallelGatewaySequenceFlow(
      final ProcessInstanceRecord value, final ElementInstance flowScopeInstance) {
    final var executableFlowNode =
        processState.getFlowElement(
            value.getProcessDefinitionKey(),
            value.getTenantId(),
            value.getElementIdBuffer(),
            ExecutableFlowNode.class);
    final var size = executableFlowNode.getIncoming().size();
    flowScopeInstance.decrementActiveSequenceFlows(size);
    elementInstanceState.updateInstance(flowScopeInstance);
  }

  private void decrementInclusiveGatewaySequenceFlow(
      final ElementInstance flowScopeInstance, final int numberOfTakenSequenceFlows) {
    flowScopeInstance.decrementActiveSequenceFlows(numberOfTakenSequenceFlows);
    elementInstanceState.updateInstance(flowScopeInstance);
  }

  private void decrementEventSubProcessSequenceFlow(
      final ProcessInstanceRecord value, final ElementInstance flowScopeInstance) {
    final var executableFlowElementContainer = getExecutableFlowElementContainer(value);
    final var executableStartEvent = executableFlowElementContainer.getStartEvents().get(0);
    if (executableStartEvent.isInterrupting()) {
      flowScopeInstance.resetActiveSequenceFlows();
    }
    elementInstanceState.updateInstance(flowScopeInstance);
  }

  private ExecutableFlowElementContainer getExecutableFlowElementContainer(
      final ProcessInstanceRecord value) {
    return processState.getFlowElement(
        value.getProcessDefinitionKey(),
        value.getTenantId(),
        value.getElementIdBuffer(),
        ExecutableFlowElementContainer.class);
  }

  private void manageMultiInstance(
      final long elementInstanceKey,
      final ElementInstance flowScopeInstance,
      final BpmnElementType flowScopeElementType) {
    if (flowScopeElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      flowScopeInstance.incrementMultiInstanceLoopCounter();
      flowScopeInstance.incrementNumberOfElementInstances();
      elementInstanceState.updateInstance(flowScopeInstance);

      final var loopCounter = flowScopeInstance.getMultiInstanceLoopCounter();
      elementInstanceState.updateInstance(
          elementInstanceKey, instance -> instance.setMultiInstanceLoopCounter(loopCounter));
    }
  }

  private void createEventScope(
      final long elementInstanceKey, final ProcessInstanceRecord elementRecord) {
    Class<? extends ExecutableFlowNode> flowElementClass = ExecutableFlowNode.class;

    if (elementRecord.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      flowElementClass = ExecutableMultiInstanceBody.class;
    }

    final var flowElement =
        processState.getFlowElement(
            elementRecord.getProcessDefinitionKey(),
            elementRecord.getTenantId(),
            elementRecord.getElementIdBuffer(),
            flowElementClass);

    if (flowElement instanceof final ExecutableCatchEventSupplier eventSupplier
        && !eventSupplier.getEvents().isEmpty()) {
      eventScopeInstanceState.createInstance(
          elementInstanceKey,
          eventSupplier.getInterruptingElementIds(),
          eventSupplier.getBoundaryElementIds());
    } else if (flowElement instanceof ExecutableJobWorkerElement
        || flowElement instanceof ExecutableActivity
        || flowElement instanceof ExecutableExclusiveGateway
        || flowElement instanceof ExecutableInclusiveGateway) {
      eventScopeInstanceState.createInstance(
          elementInstanceKey, Collections.emptySet(), Collections.emptySet());
    }
  }

  private void insertBusinessIdIndex(final ProcessInstanceRecord value) {
    final var businessId = value.getBusinessId();
    if (!businessId.isEmpty()) {
      elementInstanceState.insertProcessInstanceKeyByBusinessId(
          businessId, value.getBpmnProcessId(), value.getTenantId(), value.getProcessInstanceKey());
    }
  }
}
