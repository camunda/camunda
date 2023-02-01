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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;

public final class ElementActivationBehavior {

  public static final long NO_ANCESTOR_SCOPE_KEY = -1L;

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
   * Activates the given element.
   *
   * <p>If the element is nested inside a flow scope and there is no active instance of the flow
   * scope then it creates a new instance. This is used when modifying a process instance or
   * starting a process instance at a different place than the start event.
   *
   * <p>If there are multiple flow scope instances, then you should use {@link
   * #activateElement(ProcessInstanceRecord, AbstractFlowElement, long, BiConsumer)} to select a
   * specific ancestor.
   *
   * @param processInstanceRecord the record of the process instance
   * @param elementToActivate The element to activate
   * @return The key of the activated element instance and the keys of all it's flow scopes
   */
  public ActivatedElementKeys activateElement(
      final ProcessInstanceRecord processInstanceRecord,
      final AbstractFlowElement elementToActivate) {
    return activateElement(
        processInstanceRecord, elementToActivate, NO_ANCESTOR_SCOPE_KEY, (empty, function) -> {});
  }

  /**
   * Activates the given element.
   *
   * <p>If the element is nested inside a flow scope and there is no active instance of the flow
   * scope then it creates a new instance. This is used when modifying a process instance or
   * starting a process instance at a different place than the start event.
   *
   * <p>If there are multiple flow scope instances, then the ancestor scope key must be provided to
   * choose one.
   *
   * @param processInstanceRecord the record of the process instance
   * @param elementToActivate The element to activate
   * @param ancestorScopeKey The key of the chosen ancestor scope in case there are multiple flow
   *     scope instances
   * @param createVariablesCallback Callback to create variables at a given scope
   * @return The key of the activated element instance and the keys of all it's flow scopes
   */
  public ActivatedElementKeys activateElement(
      final ProcessInstanceRecord processInstanceRecord,
      final AbstractFlowElement elementToActivate,
      final long ancestorScopeKey,
      final BiConsumer<DirectBuffer, Long> createVariablesCallback) {
    final var activatedElementKeys = new ActivatedElementKeys();

    final var flowScopes = collectFlowScopesOfElement(elementToActivate);
    final var flowScopeKey =
        activateAncestralSubprocesses(
            processInstanceRecord,
            processInstanceRecord.getProcessInstanceKey(),
            flowScopes,
            ancestorScopeKey,
            createVariablesCallback,
            activatedElementKeys);

    final long elementInstanceKey =
        activateElementByCommand(processInstanceRecord, elementToActivate, flowScopeKey);
    createVariablesCallback.accept(elementToActivate.getId(), elementInstanceKey);
    activatedElementKeys.setElementInstanceKey(elementInstanceKey);

    return activatedElementKeys;
  }

  /** Collects all the flow scopes of an element, but excludes the root process as an element */
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

  /**
   * Activate (if needed) the subprocesses that are the direct and indirect flow scopes (ancestors)
   * of the element targeted for activation.
   *
   * <p>This method uses recursion, each time polling an element from the subprocesses parameter.
   *
   * <p>It is able to determine whether a new instance of an ancestral subprocess must be activated,
   * or that one of the existing instances can be used. In some cases this requires the
   * ancestorScopeKey to choose between multiple available instances of the same subprocess.
   *
   * <p>Note that the ancestorScopeKey is also used to create new instances of in between
   * subprocesses, if the ancestorScopeKey refers to an ancestor of that subprocess.
   *
   * @param processInstanceRecord the record of the process instance in which an element is being
   *     activated
   * @param flowScopeKey key of the element instance that should be used as direct flow scope of the
   *     next to poll subprocess from subprocesses
   * @param subprocesses the elements to activate, these are ancestors of the element targeted for
   *     activation, instances may or may not yet exist of these elements
   * @param ancestorScopeKey the key of an ancestor (indirect/direct flow scope) used for ancestor
   *     selection, determines whether new instances of subprocesses should be activated or that we
   *     can use existing ones
   * @param createVariablesCallback a callback function to create variables in the activated
   *     subprocesses, and the selected instances
   * @param activatedElementKeys collects the keys of the subprocesses encountered and/or activated
   * @return the key of the last subprocess, to be used as direct flow scope of the element targeted
   *     for activation
   */
  private long activateAncestralSubprocesses(
      final ProcessInstanceRecord processInstanceRecord,
      final long flowScopeKey,
      final Deque<ExecutableFlowElement> subprocesses,
      final long ancestorScopeKey,
      final BiConsumer<DirectBuffer, Long> createVariablesCallback,
      final ActivatedElementKeys activatedElementKeys) {

    if (subprocesses.isEmpty()) {
      return flowScopeKey;
    }
    final var nextSubprocess = subprocesses.poll();

    final var bpmnProcessId = processInstanceRecord.getBpmnProcessId();
    final Optional<Long> subprocessKey =
        findReusableSubprocessInstanceKey(
            bpmnProcessId, nextSubprocess, flowScopeKey, ancestorScopeKey);

    final long activatedInstanceKey;
    if (subprocessKey.isPresent()) {
      activatedInstanceKey = subprocessKey.get();
      createVariablesCallback.accept(nextSubprocess.getId(), activatedInstanceKey);
    } else {
      // no subprocess instance found, let's create a new one

      if (nextSubprocess.getElementType() == BpmnElementType.MULTI_INSTANCE_BODY
          || (nextSubprocess.getFlowScope() != null
              && nextSubprocess.getFlowScope().getElementType()
                  == BpmnElementType.MULTI_INSTANCE_BODY)) {
        // unsupported scenario, attempting to activate a multi-instance body or an inner instance
        throw new UnsupportedMultiInstanceBodyActivationException(
            BufferUtil.bufferAsString(nextSubprocess.getId()), bpmnProcessId);
      }

      activatedInstanceKey =
          activateFlowScope(
              processInstanceRecord, flowScopeKey, nextSubprocess, createVariablesCallback);
    }

    activatedElementKeys.addFlowScopeKey(activatedInstanceKey);

    return activateAncestralSubprocesses(
        processInstanceRecord,
        activatedInstanceKey,
        subprocesses,
        ancestorScopeKey,
        createVariablesCallback,
        activatedElementKeys);
  }

  /**
   * This method tries to find the instance of the subprocess that should be used as the flow scope
   * of the next recursion of {@link #activateAncestralSubprocesses(ProcessInstanceRecord, long,
   * Deque, long, BiConsumer, ActivatedElementKeys)}.
   *
   * <p>This method works by looking up element instances of the specific subprocess, and then
   * considers whether we can use one of the instances that are found, or whether a new instance
   * should be created instead.
   *
   * <p>If a new instance should be created, it returns an empty optional.
   *
   * @param bpmnProcessId the id of the process
   * @param subprocess the specific subprocess that we hope to find an instance of
   * @param flowScopeKey the key of the flow scope instance whose children are the only instances
   *     considered
   * @param ancestorScopeKey the key of an ancestor (indirect/direct flow scope) used for ancestor
   *     selection, determines whether we may consider existing instances, or should ignore them
   * @return optionally the key of the instance it found, otherwise an empty optional.
   */
  private Optional<Long> findReusableSubprocessInstanceKey(
      final String bpmnProcessId,
      final ExecutableFlowElement subprocess,
      final long flowScopeKey,
      final long ancestorScopeKey) {
    final List<ElementInstance> subprocessInstances =
        findElementInstances(subprocess, flowScopeKey);

    if (subprocessInstances.isEmpty()) {
      // there is no active instance of this subprocess
      return Optional.empty();
    }

    if (subprocessInstances.size() == 1) {
      // there is an active instance of this subprocess
      final var subprocessInstance = subprocessInstances.get(0);

      if (isAncestorSelected(ancestorScopeKey)
          && isAncestorOfElementInstance(ancestorScopeKey, subprocessInstance)) {
        // the subprocess instance is a descendant of the selected ancestor
        // - don't reuse this instance
        return Optional.empty();
      }

      // no ancestor selection used, or the subprocess instance isn't a descendant of it.
      // most often this means that the subprocess instance IS the selected ancestor
      // - no need to create a new instance; we can reuse this one
      return Optional.of(subprocessInstance.getKey());
    }

    // there are multiple active instances of this subprocess
    // - try to use ancestor selection
    if (!isAncestorSelected(ancestorScopeKey)) {
      // no ancestor selected
      // - reject by throwing an exception
      final var subprocessId = BufferUtil.bufferAsString(subprocess.getId());
      throw new MultipleFlowScopeInstancesFoundException(subprocessId, bpmnProcessId);
    }

    if (subprocessInstances.stream().anyMatch(instance -> instance.getKey() == ancestorScopeKey)) {
      // one of the existing subprocess instances is the selected ancestor
      // - we can reuse the selected instance
      return Optional.of(ancestorScopeKey);
    }

    if (subprocessInstances.stream()
        .anyMatch(instance -> isAncestorOfElementInstance(ancestorScopeKey, instance))) {
      // the selected ancestor is the (in)direct flow scope one of the subprocess instances
      // - don't reuse any of these instances
      return Optional.empty();
    }

    final var selectedAncestor = elementInstanceState.getInstance(ancestorScopeKey);
    final var ancestorOfAncestorInstance =
        subprocessInstances.stream()
            .filter(instance -> isAncestorOfElementInstance(instance.getKey(), selectedAncestor))
            .findAny();
    if (ancestorOfAncestorInstance.isPresent()) {
      // we found a subprocess instance that is an ancestor of the selected ancestor
      // - we can reuse that instance
      return Optional.of(ancestorOfAncestorInstance.get().getKey());
    }

    // the selected ancestor is not one of the existing subprocesses instances, nor an ancestor of
    // them, nor a descendant of them. It might be a completely unrelated element instance, or a
    // non-existing element instance. We cannot decide what to do here.
    // - reject by throwing an exception
    final var subprocessId = BufferUtil.bufferAsString(subprocess.getId());
    throw new MultipleFlowScopeInstancesFoundException(subprocessId, bpmnProcessId);
  }

  private boolean isAncestorOfElementInstance(
      final long ancestorScopeKey, final ElementInstance instance) {
    final long directFlowScopeKey = instance.getValue().getFlowScopeKey();
    if (directFlowScopeKey == -1L) {
      // the instance has no flow scope, so it does not have an ancestor
      return false;
    }

    if (directFlowScopeKey == ancestorScopeKey) {
      // the instance's direct flow scope is the ancestor
      return true;
    }

    final var directFlowScope = elementInstanceState.getInstance(directFlowScopeKey);
    return isAncestorOfElementInstance(ancestorScopeKey, directFlowScope);
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
      final ExecutableFlowElement flowScope,
      final BiConsumer<DirectBuffer, Long> createVariablesCallback) {
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
        processInstanceRecord,
        flowScope,
        elementInstanceKey,
        elementInstanceFlowScopeKey,
        createVariablesCallback);

    return elementInstanceKey;
  }

  private void activateFlowScopeByEvents(
      final ProcessInstanceRecord processInstanceRecord,
      final ExecutableFlowElement element,
      final long elementInstanceKey,
      final long flowScopeKey,
      final BiConsumer<DirectBuffer, Long> createVariablesCallback) {

    final var elementRecord = createElementRecord(processInstanceRecord, element, flowScopeKey);

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, elementRecord);
    createVariablesCallback.accept(element.getId(), elementInstanceKey);
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
        .setParentElementInstanceKey(-1L)
        .setBpmnEventType(elementToActivate.getEventType());

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
          catchEventBehavior.subscribeToEvents(bpmnElementContext, catchEventSupplier);

      if (subscribedOrFailure.isLeft()) {
        final var message =
            "Expected to subscribe to catch event(s) of '%s' but %s"
                .formatted(
                    BufferUtil.bufferAsString(element.getId()),
                    subscribedOrFailure.getLeft().getMessage());
        throw new EventSubscriptionException(message);
      }
    }
  }

  private static boolean isAncestorSelected(final long ancestorScopeKey) {
    return ancestorScopeKey != NO_ANCESTOR_SCOPE_KEY;
  }

  public static class ActivatedElementKeys {
    private final Set<Long> flowScopeKeys = new HashSet<>();
    private Long elementInstanceKey;

    private void addFlowScopeKey(final Long flowScopeKey) {
      flowScopeKeys.add(flowScopeKey);
    }

    public Long getElementInstanceKey() {
      return elementInstanceKey;
    }

    private void setElementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
    }

    public Set<Long> getFlowScopeKeys() {
      return flowScopeKeys;
    }
  }
}
