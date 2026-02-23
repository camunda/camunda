/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.AwaitProcessInstanceResultMetadata;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface MutableElementInstanceState extends ElementInstanceState {

  ElementInstance newInstance(long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  ElementInstance newInstance(
      ElementInstance parent, long key, ProcessInstanceRecord value, ProcessInstanceIntent state);

  void removeInstance(long key);

  void createInstance(ElementInstance instance);

  void updateInstance(ElementInstance scopeInstance);

  void updateInstance(long key, Consumer<ElementInstance> modifier);

  void setAwaitResultRequestMetadata(
      long processInstanceKey, AwaitProcessInstanceResultMetadata metadata);

  /**
   * Increments the number that counts how often the given sequence flow has been taken.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not. It should be
   * incremented when one of the incoming sequence flows is taken.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway that is the target of the sequence flow
   * @param sequenceFlowElementId the element id of the sequence flow that is taken
   */
  void incrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId);

  /**
   * Decrements the numbers that counts how often a sequence flow of the given gateway has been
   * taken.
   *
   * <p>The number helps to determine if a parallel gateway can be activated or not. It should be
   * decremented when the gateway is activated.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway
   */
  void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId);

  /**
   * Decrements the numbers that counts how often a sequence flow of the given gateway has been
   * taken.
   *
   * @param flowScopeKey the key of the flow scope that contains the gateway
   * @param gatewayElementId the element id of the gateway that is the target of the sequence flow
   * @param sequenceFlowElementId the element id of the sequence flow that is taken
   */
  void decrementNumberOfTakenSequenceFlows(
      final long flowScopeKey,
      final DirectBuffer gatewayElementId,
      final DirectBuffer sequenceFlowElementId);

  /**
   * Inserts a new reference from process instance key to process definition key.
   *
   * <p>This makes it possible to query for all process instances of a specific process definition
   * using {@link ElementInstanceState#getProcessInstanceKeysByDefinitionKey(long)}.
   *
   * @param processInstanceKey the key of the process instance to insert the reference for
   * @param processDefinitionKey the key of the process definition to insert the reference for
   */
  void insertProcessInstanceKeyByDefinitionKey(long processInstanceKey, long processDefinitionKey);

  /**
   * Deletes the reference between process instance key and process definition key.
   *
   * <p>This makes it possible to query for all process instances of a specific process definition
   * using {@link ElementInstanceState#getProcessInstanceKeysByDefinitionKey(long)}.
   *
   * @param processInstanceKey the key of the process instance to delete the reference for
   * @param processDefinitionKey the key of the process definition to delete the reference for
   */
  void deleteProcessInstanceKeyByDefinitionKey(long processInstanceKey, long processDefinitionKey);

  /**
   * Stores runtime instructions for the process instance with the given key.
   *
   * <p>This method is used to store runtime instructions during the creation of a process instance,
   * when the instance does not yet exist in the state.
   *
   * @param processInstanceKey the key of the process instance to which the runtime instructions
   *     belong
   * @param runtimeInstructions the list of runtime instructions to add
   */
  void addRuntimeInstructions(
      long processInstanceKey,
      List<ProcessInstanceCreationRuntimeInstructionValue> runtimeInstructions);

  /**
   * Inserts a mapping from business id to process instance key. This is used to enforce uniqueness
   * of business id per process definition (scoped by tenant).
   *
   * @param businessId the business id
   * @param processDefinitionKey the process definition key
   * @param tenantId the tenant id
   * @param processInstanceKey the process instance key
   */
  void insertProcessInstanceKeyByBusinessId(
      String businessId, long processDefinitionKey, String tenantId, long processInstanceKey);

  /**
   * Deletes the mapping from business id to process instance key. This is used to enforce
   * uniqueness of business id per process definition (scoped by tenant).
   *
   * @param businessId the business id
   * @param processDefinitionKey the process definition key
   * @param tenantId the tenant id
   * @param processInstanceKey the process instance key
   */
  void deleteProcessInstanceKeyMappingByBusinessId(
      String businessId, long processDefinitionKey, String tenantId, long processInstanceKey);
}
