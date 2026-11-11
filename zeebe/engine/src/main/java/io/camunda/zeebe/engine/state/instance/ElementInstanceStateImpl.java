/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/**
 * The element instance state, independent of where it is stored: keeps the child counts of the
 * parents and the variable scopes of the instances, and removes an instance's data with the
 * instance. The {@link ElementInstanceStore} persists it.
 */
public final class ElementInstanceStateImpl implements MutableElementInstanceState {

  private final ElementInstanceStore store;
  private final MutableVariableState variableState;

  public ElementInstanceStateImpl(
      final ElementInstanceStore store, final MutableVariableState variableState) {
    this.store = store;
    this.variableState = variableState;
  }

  @Override
  public ElementInstance newInstance(
      final long key, final ProcessInstanceRecord value, final ProcessInstanceIntent state) {
    return newInstance(null, key, value, state);
  }

  @Override
  public ElementInstance newInstance(
      final ElementInstance parent,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {

    final ElementInstance instance;
    if (parent == null) {
      instance = new ElementInstance(key, state, value);
    } else {
      instance = new ElementInstance(key, parent, state, value);
      updateInstance(parent);
    }
    createInstance(instance);

    return instance;
  }

  @Override
  public void removeInstance(final long key) {
    final var instance = store.getSharedInstance(key);
    if (instance == null) {
      return;
    }
    // the shared instance is overwritten by the read of the parent below
    final long parent = instance.getParentKey();
    final var elementType = instance.getValue().getBpmnElementType();
    store.deleteInstance(instance);
    variableState.removeScope(key);
    if (elementType == BpmnElementType.PROCESS) {
      // only process instances can have one, stored under their key
      store.deleteAwaitResult(key);
    }
    if (elementType.isContainerElement()) {
      // taken sequence flows belong to the flow scope of their gateway, which contains elements
      store.deleteTakenSequenceFlows(key);
    }

    if (parent > 0) {
      final var parentInstance = store.getSharedInstance(parent);
      if (parentInstance == null) {
        final var errorMsg =
            "Expected to find parent instance for element instance with key %d, but none was found.";
        throw new IllegalStateException(String.format(errorMsg, parent));
      }
      parentInstance.decrementChildCount();
      updateInstance(parentInstance);
    }
  }

  @Override
  public void createInstance(final ElementInstance instance) {
    store.insertInstance(instance);
    variableState.createScope(instance.getKey(), instance.getParentKey());
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    store.updateInstance(scopeInstance);
  }

  @Override
  public void updateInstance(final long key, final Consumer<ElementInstance> modifier) {
    final var scopeInstance = store.getSharedInstance(key);
    modifier.accept(scopeInstance);
    updateInstance(scopeInstance);
  }

  @Override
  public void setAwaitResultRequestMetadata(
      final long processInstanceKey,
      final String tenantId,
      final AwaitProcessInstanceResultMetadata metadata) {
    store.insertAwaitResult(processInstanceKey, tenantId, metadata);
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    store.incrementTakenSequenceFlowCount(flowScopeKey, gatewayElementId, sequenceFlowElementId);
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    store.decrementTakenSequenceFlowCounts(flowScopeKey, gatewayElementId);
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    store.decrementTakenSequenceFlowCount(flowScopeKey, gatewayElementId, sequenceFlowElementId);
  }

  @Override
  public void insertProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    store.insertProcessInstanceKeyByDefinitionKey(processInstanceKey, processDefinitionKey);
  }

  @Override
  public void deleteProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    store.deleteProcessInstanceKeyByDefinitionKey(processInstanceKey, processDefinitionKey);
  }

  @Override
  public void addRuntimeInstructions(
      final long processInstanceKey,
      final String tenantId,
      final List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions) {
    store.insertRuntimeInstructions(processInstanceKey, tenantId, runtimeInstructions);
  }

  @Override
  public void removeRuntimeInstructions(final long processInstanceKey) {
    store.deleteRuntimeInstructions(processInstanceKey);
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    store.insertProcessInstanceKeyByBusinessId(
        businessId, processDefinitionId, tenantId, processInstanceKey);
  }

  @Override
  public void deleteProcessInstanceKeyMappingByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    store.deleteProcessInstanceKeyByBusinessId(
        businessId, processDefinitionId, tenantId, processInstanceKey);
  }

  @Override
  public ElementInstance getInstance(final long key) {
    return store.getInstance(key);
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    return store.getChildren(parentKey);
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor) {
    forEachChild(parentKey, startAtKey, visitor, (elementInstance) -> true);
  }

  @Override
  public void forEachChildKey(final long parentKey, final Function<Long, Boolean> visitor) {
    store.forEachChildKey(parentKey, visitor);
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor,
      final Predicate<ElementInstance> filter) {
    store.forEachChild(parentKey, startAtKey, visitor, filter);
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    return store.getAwaitResult(processInstanceKey);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return store.countTakenSequenceFlows(flowScopeKey, gatewayElementId);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(final long flowScopeKey) {
    return store.countTakenSequenceFlows(flowScopeKey);
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return store.getTakenSequenceFlows(flowScopeKey, gatewayElementId);
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    store.visitTakenSequenceFlows(flowScopeKey, visitor);
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    return store.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);
  }

  @Override
  public boolean hasActiveProcessInstances(
      final long processDefinitionKey, final List<Long> bannedInstances) {
    // a banned instance should not be considered as active
    return store.hasProcessInstances(processDefinitionKey, bannedInstances);
  }

  @Override
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    final var instructions = store.getRuntimeInstructions(processInstanceKey);
    if (instructions == null) {
      return List.of();
    }
    return instructions.getRuntimeInstructions().stream()
        .filter(instruction -> instruction.getAfterElementId().equals(elementId))
        .toList();
  }

  @Override
  public boolean hasActiveProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    return store.hasProcessInstanceWithBusinessId(
        businessId, processDefinitionId, tenantId, ignoreWhen);
  }

  @Override
  public long getActiveProcessInstanceCount() {
    return store.getActiveProcessInstanceCount();
  }
}
