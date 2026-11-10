/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState.TakenSequenceFlowVisitor;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Persistence of the element instance state: reads and writes of the stored data, without the
 * domain rules of {@link ElementInstanceStateImpl} (child counts, variable scopes, cleaning up an
 * instance's data on removal).
 *
 * <p>A store may keep indexes derived from the element instances, maintained by {@link
 * #insertInstance(ElementInstance)} and {@link #deleteInstance(ElementInstance)}: the process
 * instances by process definition and the number of active process instances.
 *
 * <p>Unless documented otherwise, returned objects may be backed by buffers that the store reuses
 * on its next read: callers must not keep them across another read of the store.
 */
public interface ElementInstanceStore {

  /**
   * @return a new instance owned by the caller, or null if the key is negative or there is none
   */
  @Nullable ElementInstance getInstance(long key);

  /**
   * @return the instance, possibly a shared object that the next read overwrites, or null if there
   *     is none
   */
  @Nullable ElementInstance getSharedInstance(long key);

  /** Inserts the instance and its derived indexes. */
  void insertInstance(ElementInstance instance);

  void updateInstance(ElementInstance instance);

  /** Deletes the existing instance and its derived indexes. */
  void deleteInstance(ElementInstance instance);

  /**
   * @return new instances of the children of the parent, ordered by key; empty if the parent
   *     doesn't exist
   */
  List<ElementInstance> getChildren(long parentKey);

  /**
   * Visits the children of the parent that match the filter, ordered by key, starting at the given
   * child key (-1 for the first child), until the visitor returns false. The visited children are
   * new instances.
   */
  void forEachChild(
      long parentKey,
      long startAtKey,
      BiFunction<Long, ElementInstance, Boolean> visitor,
      Predicate<ElementInstance> filter);

  /** Visits the keys of the children of the parent, ordered, until the visitor returns false. */
  void forEachChildKey(long parentKey, Function<Long, Boolean> visitor);

  /** Counts that the sequence flow to the gateway was taken once more. */
  void incrementTakenSequenceFlowCount(
      long flowScopeKey, DirectBuffer gatewayElementId, DirectBuffer sequenceFlowElementId);

  /**
   * Counts that the sequence flow to the gateway was taken once less, removing the count when it
   * reaches zero. The sequence flow must have been taken.
   */
  void decrementTakenSequenceFlowCount(
      long flowScopeKey, DirectBuffer gatewayElementId, DirectBuffer sequenceFlowElementId);

  /** Decrements the counts of all sequence flows taken to the gateway, see above. */
  void decrementTakenSequenceFlowCounts(long flowScopeKey, DirectBuffer gatewayElementId);

  /** Deletes the counts of all taken sequence flows of the flow scope. */
  void deleteTakenSequenceFlows(long flowScopeKey);

  /** Visits the taken sequence flows of the flow scope, ordered by gateway and sequence flow. */
  void visitTakenSequenceFlows(long flowScopeKey, TakenSequenceFlowVisitor visitor);

  /**
   * @return the number of distinct sequence flows taken to the gateway of the flow scope
   */
  int countTakenSequenceFlows(long flowScopeKey, DirectBuffer gatewayElementId);

  /**
   * @return the number of distinct sequence flows taken to any gateway of the flow scope
   */
  int countTakenSequenceFlows(long flowScopeKey);

  /**
   * @return copies of the ids of the sequence flows taken to the gateway, ordered
   */
  Set<DirectBuffer> getTakenSequenceFlows(long flowScopeKey, DirectBuffer gatewayElementId);

  void insertAwaitResult(
      long processInstanceKey, String tenantId, AwaitProcessInstanceResultMetadata metadata);

  @Nullable AwaitProcessInstanceResultMetadata getAwaitResult(long processInstanceKey);

  /** Deletes the await result of the key, if there is one. */
  void deleteAwaitResult(long processInstanceKey);

  void insertRuntimeInstructions(
      long processInstanceKey,
      String tenantId,
      List<ProcessInstanceCreationRuntimeInstructionValue> instructions);

  @Nullable RuntimeInstructions getRuntimeInstructions(long processInstanceKey);

  /** Deletes the runtime instructions of the process instance, if there are any. */
  void deleteRuntimeInstructions(long processInstanceKey);

  void insertProcessInstanceKeyByDefinitionKey(long processInstanceKey, long processDefinitionKey);

  void deleteProcessInstanceKeyByDefinitionKey(long processInstanceKey, long processDefinitionKey);

  List<Long> getProcessInstanceKeysByDefinitionKey(long processDefinitionKey);

  /**
   * @return whether the process definition has a process instance that isn't excluded
   */
  boolean hasProcessInstances(long processDefinitionKey, List<Long> excludedInstances);

  void insertProcessInstanceKeyByBusinessId(
      String businessId, String processDefinitionId, String tenantId, long processInstanceKey);

  void deleteProcessInstanceKeyByBusinessId(
      String businessId, String processDefinitionId, String tenantId, long processInstanceKey);

  /**
   * @return whether a process instance with the business id exists that isn't ignored
   */
  boolean hasProcessInstanceWithBusinessId(
      String businessId, String processDefinitionId, String tenantId, Predicate<Long> ignoreWhen);

  long getActiveProcessInstanceCount();
}
