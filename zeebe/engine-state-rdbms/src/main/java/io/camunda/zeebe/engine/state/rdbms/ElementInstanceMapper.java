/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the element instance tables, see {@code
 * db/mybatis/engine-state/ElementInstanceMapper.xml}. Every statement is scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link io.camunda.zeebe.db.impl.jdbc.EntityCache}
 * requires: payloads are serialized msgpack and must not be modified.
 *
 * <p>Every row carries the process instance, root process instance and tenant it belongs to, so all
 * rows of a process instance, a process instance tree or a tenant can be found in every table. They
 * never change during a row's lifetime: they are set on insert, and updates keep them.
 */
interface ElementInstanceMapper {

  // CAM_RU_ELEMENT_INSTANCE

  @Nullable ElementInstanceRow selectElementInstance(
      @Param("partitionId") int partitionId, @Param("key") long key);

  List<ElementInstanceRow> selectChildren(
      @Param("partitionId") int partitionId, @Param("parentKey") long parentKey);

  List<Long> selectChildKeys(
      @Param("partitionId") int partitionId, @Param("parentKey") long parentKey);

  List<Long> selectProcessInstanceKeysByBusinessId(
      @Param("partitionId") int partitionId,
      @Param("tenantId") String tenantId,
      @Param("bpmnProcessId") String bpmnProcessId,
      @Param("businessId") String businessId);

  List<Long> selectProcessInstanceKeysByDefinition(
      @Param("partitionId") int partitionId,
      @Param("processDefinitionKey") long processDefinitionKey);

  /** At most one process instance of the definition, not one of the excluded keys. */
  List<Long> selectAnyProcessInstanceByDefinition(
      @Param("partitionId") int partitionId,
      @Param("processDefinitionKey") long processDefinitionKey,
      @Param("excludedKeys") Long[] excludedKeys);

  long countProcessInstances(
      @Param("partitionId") int partitionId, @Param("excludedKeys") Long[] excludedKeys);

  void insertElementInstance(
      @Param("partitionId") int partitionId, @Param("row") ElementInstanceRow row);

  void updateElementInstance(
      @Param("partitionId") int partitionId, @Param("row") ElementInstanceRow row);

  void deleteElementInstance(@Param("partitionId") int partitionId, @Param("key") long key);

  // CAM_RU_TAKEN_SEQUENCE_FLOW

  @Nullable TakenSequenceFlowRow selectTakenSequenceFlow(
      @Param("partitionId") int partitionId, @Param("key") TakenSequenceFlowKey key);

  /** The taken sequence flows of the flow scope, of all gateways if the gateway id is null. */
  List<TakenSequenceFlowRow> selectTakenSequenceFlows(
      @Param("partitionId") int partitionId,
      @Param("flowScopeKey") long flowScopeKey,
      @Param("gatewayElementId") @Nullable String gatewayElementId);

  void insertTakenSequenceFlow(
      @Param("partitionId") int partitionId, @Param("row") TakenSequenceFlowRow row);

  void updateTakenSequenceFlow(
      @Param("partitionId") int partitionId, @Param("row") TakenSequenceFlowRow row);

  void deleteTakenSequenceFlow(
      @Param("partitionId") int partitionId, @Param("key") TakenSequenceFlowKey key);

  // CAM_RU_AWAIT_PROCESS_INSTANCE_RESULT

  @Nullable PayloadRow selectAwaitResult(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  void insertAwaitResult(@Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void updateAwaitResult(@Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void deleteAwaitResult(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  // CAM_RU_RUNTIME_INSTRUCTIONS

  @Nullable PayloadRow selectRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  void insertRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void updateRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void deleteRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  /**
   * An element instance: the serialized {@link ElementInstance} plus the columns derived from it
   * for queries. The root process instance key is the process instance key for a root process
   * instance, and for instances created before the record carried a root process instance key. The
   * business id is null unless the row is a root process instance with a business id.
   */
  record ElementInstanceRow(
      long key,
      long parentKey,
      long processInstanceKey,
      long rootProcessInstanceKey,
      long processDefinitionKey,
      String tenantId,
      String bpmnProcessId,
      @Nullable String businessId,
      String elementId,
      short bpmnElementType,
      short state,
      byte[] payload) {}

  record TakenSequenceFlowKey(
      long flowScopeKey, String gatewayElementId, String sequenceFlowElementId) {}

  /** How often a sequence flow was taken; belongs to the process instance of its flow scope. */
  record TakenSequenceFlowRow(
      long flowScopeKey,
      String gatewayElementId,
      String sequenceFlowElementId,
      long processInstanceKey,
      long rootProcessInstanceKey,
      String tenantId,
      int timesTaken) {

    TakenSequenceFlowKey key() {
      return new TakenSequenceFlowKey(flowScopeKey, gatewayElementId, sequenceFlowElementId);
    }

    TakenSequenceFlowRow withTimesTaken(final int newTimesTaken) {
      return new TakenSequenceFlowRow(
          flowScopeKey,
          gatewayElementId,
          sequenceFlowElementId,
          processInstanceKey,
          rootProcessInstanceKey,
          tenantId,
          newTimesTaken);
    }
  }

  /** A serialized value keyed by process instance key. */
  record PayloadRow(long key, long rootProcessInstanceKey, String tenantId, byte[] payload) {}
}
