/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the relational engine state tables, see {@code
 * db/mybatis/engine-state/EngineStateMapper.xml}. Every statement is scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link io.camunda.zeebe.db.impl.jdbc.EntityCache}
 * requires: payloads are serialized msgpack and must not be modified.
 */
interface EngineStateMapper {

  // element_instance

  @Nullable ElementInstanceRow selectElementInstance(
      @Param("partitionId") int partitionId, @Param("key") long key);

  List<ElementInstanceRow> selectChildren(
      @Param("partitionId") int partitionId, @Param("parentKey") long parentKey);

  List<Long> selectChildKeys(
      @Param("partitionId") int partitionId, @Param("parentKey") long parentKey);

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

  // taken_sequence_flow

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

  // await_process_instance_result

  @Nullable PayloadRow selectAwaitResult(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  void insertAwaitResult(@Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void updateAwaitResult(@Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void deleteAwaitResult(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  // runtime_instructions

  @Nullable PayloadRow selectRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  void insertRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void updateRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("row") PayloadRow row);

  void deleteRuntimeInstructions(
      @Param("partitionId") int partitionId, @Param("key") long processInstanceKey);

  // process_instance_by_business_id

  @Nullable BusinessIdRow selectBusinessId(
      @Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  List<BusinessIdRow> selectBusinessIds(
      @Param("partitionId") int partitionId,
      @Param("businessId") String businessId,
      @Param("processDefinitionId") String processDefinitionId,
      @Param("tenantId") String tenantId);

  void insertBusinessId(@Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  void updateBusinessId(@Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  void deleteBusinessId(@Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  /**
   * An element instance: the serialized {@link ElementInstance} plus the columns derived from it
   * for queries.
   */
  record ElementInstanceRow(
      long key,
      long parentKey,
      long processInstanceKey,
      long processDefinitionKey,
      short bpmnElementType,
      short state,
      byte[] payload) {}

  record TakenSequenceFlowKey(
      long flowScopeKey, String gatewayElementId, String sequenceFlowElementId) {}

  record TakenSequenceFlowRow(
      long flowScopeKey, String gatewayElementId, String sequenceFlowElementId, int timesTaken) {

    TakenSequenceFlowKey key() {
      return new TakenSequenceFlowKey(flowScopeKey, gatewayElementId, sequenceFlowElementId);
    }

    TakenSequenceFlowRow withTimesTaken(final int newTimesTaken) {
      return new TakenSequenceFlowRow(
          flowScopeKey, gatewayElementId, sequenceFlowElementId, newTimesTaken);
    }
  }

  /** A serialized value keyed by process instance key. */
  record PayloadRow(long key, byte[] payload) {}

  /** An entry of the business id index; the whole row is its key. */
  record BusinessIdRow(
      String businessId, String processDefinitionId, String tenantId, long processInstanceKey) {}
}
