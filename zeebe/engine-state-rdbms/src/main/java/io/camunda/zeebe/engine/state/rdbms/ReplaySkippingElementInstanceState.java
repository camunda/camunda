/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.RuntimeInstructionValue;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Leaves the element instance rows alone while the replay applies events whose changes the database
 * already contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators
 * do nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingElementInstanceState implements MutableElementInstanceState {

  private final MutableElementInstanceState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingElementInstanceState(
      final MutableElementInstanceState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  /**
   * When skipping, returns the instance the delegate would store, without storing it: the
   * activating appliers modify it and pass it to {@link #updateInstance}, which skips as well.
   */
  @Override
  public ElementInstance newInstance(
      final long key, final ProcessInstanceRecord value, final ProcessInstanceIntent state) {
    if (skip()) {
      return new ElementInstance(key, state, value);
    }
    return delegate.newInstance(key, value, state);
  }

  /**
   * When skipping, returns the instance the delegate would store, without storing it. Like the
   * delegate, it counts the child on the given parent, which the caller only updates through {@link
   * #updateInstance}, a no-op too.
   */
  @Override
  public ElementInstance newInstance(
      final @Nullable ElementInstance parent,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {
    if (skip()) {
      return new ElementInstance(key, parent, state, value);
    }
    return delegate.newInstance(parent, key, value, state);
  }

  @Override
  public void removeInstance(final long key) {
    if (!skip()) {
      delegate.removeInstance(key);
    }
  }

  @Override
  public void createInstance(final ElementInstance instance) {
    if (!skip()) {
      delegate.createInstance(instance);
    }
  }

  @Override
  public void updateInstance(final ElementInstance scopeInstance) {
    if (!skip()) {
      delegate.updateInstance(scopeInstance);
    }
  }

  @Override
  public void updateInstance(final long key, final Consumer<ElementInstance> modifier) {
    if (!skip()) {
      delegate.updateInstance(key, modifier);
    }
  }

  @Override
  public void setAwaitResultRequestMetadata(
      final long processInstanceKey,
      final String tenantId,
      final AwaitProcessInstanceResultMetadata metadata) {
    if (!skip()) {
      delegate.setAwaitResultRequestMetadata(processInstanceKey, tenantId, metadata);
    }
  }

  @Override
  public void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    if (!skip()) {
      delegate.incrementNumberOfTakenSequenceFlows(
          flowScopeKey, gatewayElementId, sequenceFlowElementId);
    }
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    if (!skip()) {
      delegate.decrementNumberOfTakenSequenceFlows(flowScopeKey, gatewayElementId);
    }
  }

  @Override
  public void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId) {
    if (!skip()) {
      delegate.decrementNumberOfTakenSequenceFlows(
          flowScopeKey, gatewayElementId, sequenceFlowElementId);
    }
  }

  @Override
  public void insertProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    if (!skip()) {
      delegate.insertProcessInstanceKeyByDefinitionKey(processInstanceKey, processDefinitionKey);
    }
  }

  @Override
  public void deleteProcessInstanceKeyByDefinitionKey(
      final long processInstanceKey, final long processDefinitionKey) {
    if (!skip()) {
      delegate.deleteProcessInstanceKeyByDefinitionKey(processInstanceKey, processDefinitionKey);
    }
  }

  @Override
  public void addRuntimeInstructions(
      final long processInstanceKey,
      final String tenantId,
      final List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions) {
    if (!skip()) {
      delegate.addRuntimeInstructions(processInstanceKey, tenantId, runtimeInstructions);
    }
  }

  @Override
  public void removeRuntimeInstructions(final long processInstanceKey) {
    if (!skip()) {
      delegate.removeRuntimeInstructions(processInstanceKey);
    }
  }

  @Override
  public void insertProcessInstanceKeyByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    if (!skip()) {
      delegate.insertProcessInstanceKeyByBusinessId(
          businessId, processDefinitionId, tenantId, processInstanceKey);
    }
  }

  @Override
  public void deleteProcessInstanceKeyMappingByBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final long processInstanceKey) {
    if (!skip()) {
      delegate.deleteProcessInstanceKeyMappingByBusinessId(
          businessId, processDefinitionId, tenantId, processInstanceKey);
    }
  }

  /**
   * When skipping, an instance whose row is gone was removed after the replayed event, which refers
   * to it because it existed then. Instead of null, which appliers dereference without a check
   * where the instance must exist (e.g. the flow scope of a taken sequence flow), it returns a
   * detached instance with this key and an empty record: the appliers only modify it and update it,
   * which is skipped. A key without an instance (≤ 0) still returns null.
   */
  @Override
  public @Nullable ElementInstance getInstance(final long key) {
    final var instance = delegate.getInstance(key);
    if (instance == null && key > 0 && skip()) {
      return new ElementInstance(
          key, ProcessInstanceIntent.ELEMENT_ACTIVATED, new ProcessInstanceRecord());
    }
    return instance;
  }

  @Override
  public List<ElementInstance> getChildren(final long parentKey) {
    return delegate.getChildren(parentKey);
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor) {
    delegate.forEachChild(parentKey, startAtKey, visitor);
  }

  @Override
  public void forEachChildKey(final long parentKey, final Function<Long, Boolean> visitor) {
    delegate.forEachChildKey(parentKey, visitor);
  }

  @Override
  public void forEachChild(
      final long parentKey,
      final long startAtKey,
      final BiFunction<Long, ElementInstance, Boolean> visitor,
      final Predicate<ElementInstance> filter) {
    delegate.forEachChild(parentKey, startAtKey, visitor, filter);
  }

  @Override
  public AwaitProcessInstanceResultMetadata getAwaitResultRequestMetadata(
      final long processInstanceKey) {
    return delegate.getAwaitResultRequestMetadata(processInstanceKey);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return delegate.getNumberOfTakenSequenceFlows(flowScopeKey, gatewayElementId);
  }

  @Override
  public int getNumberOfTakenSequenceFlows(final long flowScopeKey) {
    return delegate.getNumberOfTakenSequenceFlows(flowScopeKey);
  }

  @Override
  public Set<DirectBuffer> getTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return delegate.getTakenSequenceFlows(flowScopeKey, gatewayElementId);
  }

  @Override
  public void visitTakenSequenceFlows(
      final long flowScopeKey, final TakenSequenceFlowVisitor visitor) {
    delegate.visitTakenSequenceFlows(flowScopeKey, visitor);
  }

  @Override
  public List<Long> getProcessInstanceKeysByDefinitionKey(final long processDefinitionKey) {
    return delegate.getProcessInstanceKeysByDefinitionKey(processDefinitionKey);
  }

  @Override
  public boolean hasActiveProcessInstances(
      final long processDefinitionKey, final List<Long> bannedInstances) {
    return delegate.hasActiveProcessInstances(processDefinitionKey, bannedInstances);
  }

  @Override
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    return delegate.getRuntimeInstructionsForElementId(processInstanceKey, elementId);
  }

  @Override
  public boolean hasActiveProcessInstanceWithBusinessId(
      final String businessId,
      final String processDefinitionId,
      final String tenantId,
      final Predicate<Long> ignoreWhen) {
    return delegate.hasActiveProcessInstanceWithBusinessId(
        businessId, processDefinitionId, tenantId, ignoreWhen);
  }

  @Override
  public long getActiveProcessInstanceCount() {
    return delegate.getActiveProcessInstanceCount();
  }
}
