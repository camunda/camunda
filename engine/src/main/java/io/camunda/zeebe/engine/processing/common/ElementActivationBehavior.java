/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public final class ElementActivationBehavior {

  private final SideEffectQueue sideEffectQueue = new SideEffectQueue();

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final CatchEventBehavior catchEventBehavior;

  private final ElementInstanceState elementInstanceState;

  public ElementActivationBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CatchEventBehavior catchEventBehavior,
      final ElementInstanceState elementInstanceState) {
    this.keyGenerator = keyGenerator;
    this.catchEventBehavior = catchEventBehavior;
    this.elementInstanceState = elementInstanceState;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  /**
   * Activates the given element. If the element is nested inside a flow scope and there is no
   * active instance of the flow scope then it creates a new instance. This is used when modifying a
   * process instance or starting a process instance at a different place than the start event.
   *
   * @param processInstanceRecord the record of the process instance
   * @param elementToActivate The element to activate
   * @return The key of the activated element instance
   */
  public long activateElement(
      final ProcessInstanceRecord processInstanceRecord,
      final AbstractFlowElement elementToActivate) {

    final var flowScopes = collectFlowScopesOfElement(elementToActivate);

    final var flowScopeKey =
        activateFlowScopes(
            processInstanceRecord, processInstanceRecord.getProcessInstanceKey(), flowScopes);

    final long elementInstanceKey =
        activateElementByCommand(processInstanceRecord, elementToActivate, flowScopeKey);

    // applying the side effects is part of creating the event subscriptions
    sideEffectQueue.flush();

    return elementInstanceKey;
  }

  private Deque<ExecutableFlowElement> collectFlowScopesOfElement(
      final ExecutableFlowElement element) {
    final Deque<ExecutableFlowElement> flowScopes = new ArrayDeque<>();

    ExecutableFlowElement currentElement = element.getFlowScope();

    while (currentElement != null) {
      flowScopes.addFirst(currentElement);

      currentElement = currentElement.getFlowScope();
    }

    return flowScopes;
  }

  private long activateFlowScopes(
      final ProcessInstanceRecord processInstanceRecord,
      final long flowScopeKey,
      final Deque<ExecutableFlowElement> flowScopes) {

    if (flowScopes.isEmpty()) {
      return flowScopeKey;
    }
    final var flowScope = flowScopes.poll();

    final List<ElementInstance> elementInstancesOfScope =
        findElementInstances(flowScope, flowScopeKey);

    if (elementInstancesOfScope.isEmpty()) {
      // there is no active instance of this flow scope
      // - create/activate a new instance and continue with the remaining flow scopes
      final long elementInstanceKey =
          activateFlowScope(processInstanceRecord, flowScopeKey, flowScope);
      return activateFlowScopes(processInstanceRecord, elementInstanceKey, flowScopes);

    } else if (elementInstancesOfScope.size() == 1) {
      // there is an active instance of this flow scope
      // - no need to create a new instance; continue with the remaining flow scopes
      final var elementInstance = elementInstancesOfScope.get(0);
      return activateFlowScopes(processInstanceRecord, elementInstance.getKey(), flowScopes);

    } else {
      // todo: deal with multiple flow scopes found without ancestor selection (#10008)
      final var flowScopeId = BufferUtil.bufferAsString(flowScope.getId());
      throw new UnsupportedOperationException(
          "Found more than one flow scope instance with id '%s'.".formatted(flowScopeId));
    }
  }

  private List<ElementInstance> findElementInstances(
      final ExecutableFlowElement element, final long flowScopeKey) {

    if (isProcess(element)) {
      return Optional.ofNullable(elementInstanceState.getInstance(flowScopeKey))
          .map(List::of)
          .orElse(Collections.emptyList());

    } else {
      return elementInstanceState.getChildren(flowScopeKey).stream()
          .filter(instance -> instance.getValue().getElementIdBuffer().equals(element.getId()))
          .toList();
    }
  }

  private static boolean isProcess(final ExecutableFlowElement element) {
    return element.getElementType() == BpmnElementType.PROCESS;
  }

  private long activateFlowScope(
      final ProcessInstanceRecord processInstanceRecord,
      final long flowScopeKey,
      final ExecutableFlowElement flowScope) {
    final long elementInstanceKey;
    final long elementInstanceFlowScopeKey;

    if (isProcess(flowScope)) {
      elementInstanceKey = processInstanceRecord.getProcessInstanceKey();
      elementInstanceFlowScopeKey = -1L;

    } else {
      elementInstanceKey = keyGenerator.nextKey();
      elementInstanceFlowScopeKey = flowScopeKey;
    }

    activateFlowScopeByEvents(
        processInstanceRecord, flowScope, elementInstanceKey, elementInstanceFlowScopeKey);

    return elementInstanceKey;
  }

  private void activateFlowScopeByEvents(
      final ProcessInstanceRecord processInstanceRecord,
      final ExecutableFlowElement element,
      final long elementInstanceKey,
      final long flowScopeKey) {

    final var elementRecord = createElementRecord(processInstanceRecord, element, flowScopeKey);

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, elementRecord);
    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, elementRecord);

    createEventSubscriptions(element, elementRecord, elementInstanceKey);
  }

  private long activateElementByCommand(
      final ProcessInstanceRecord processInstanceRecord,
      final AbstractFlowElement elementToActivate,
      final long flowScopeKey) {

    final var elementInstanceKey = keyGenerator.nextKey();
    final var elementRecord =
        createElementRecord(processInstanceRecord, elementToActivate, flowScopeKey);
    commandWriter.appendFollowUpCommand(
        elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, elementRecord);

    return elementInstanceKey;
  }

  private ProcessInstanceRecord createElementRecord(
      final ProcessInstanceRecord processInstanceRecord,
      final ExecutableFlowElement elementToActivate,
      final long flowScopeKey) {
    final var elementInstanceRecord = new ProcessInstanceRecord();
    // take the properties from the process instance
    elementInstanceRecord.wrap(processInstanceRecord);
    // override the properties for the specific element
    elementInstanceRecord
        .setElementId(elementToActivate.getId())
        .setBpmnElementType(elementToActivate.getElementType())
        .setFlowScopeKey(flowScopeKey)
        .setParentProcessInstanceKey(-1L)
        .setParentElementInstanceKey(-1L);

    return elementInstanceRecord;
  }

  /**
   * Create the event subscriptions of the given element. Assuming that the element instance is in
   * state ACTIVATED.
   */
  private void createEventSubscriptions(
      final ExecutableFlowElement element,
      final ProcessInstanceRecord elementRecord,
      final long elementInstanceKey) {

    if (element instanceof ExecutableCatchEventSupplier catchEventSupplier) {
      final var bpmnElementContext = new BpmnElementContextImpl();
      bpmnElementContext.init(
          elementInstanceKey, elementRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

      final Either<Failure, ?> subscribedOrFailure =
          catchEventBehavior.subscribeToEvents(
              bpmnElementContext, catchEventSupplier, sideEffectQueue);

      if (subscribedOrFailure.isLeft()) {
        final var message =
            "expected to subscribe to catch event(s) of '%s' but %s"
                .formatted(
                    BufferUtil.bufferAsString(element.getId()),
                    subscribedOrFailure.getLeft().getMessage());
        throw new EventSubscriptionException(message);
      }
    }
  }
}
