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
 * MyBatis mapper of the relational engine state tables, see {@code
 * db/mybatis/engine-state/EngineStateMapper.xml}. Every statement is scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link io.camunda.zeebe.db.impl.jdbc.EntityCache}
 * requires: payloads are serialized msgpack and must not be modified.
 *
 * <p>Every row carries the process instance, root process instance and tenant it belongs to, so all
 * rows of a process instance, a process instance tree or a tenant can be found in every table. They
 * never change during a row's lifetime: they are set on insert, and updates keep them.
 */
interface EngineStateMapper {

  // CAM_RU_ELEMENT_INSTANCE

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

  // CAM_RU_PROCESS_INSTANCE_BY_BUSINESS_ID

  @Nullable BusinessIdRow selectBusinessId(
      @Param("partitionId") int partitionId, @Param("key") BusinessIdKey key);

  List<BusinessIdRow> selectBusinessIds(
      @Param("partitionId") int partitionId,
      @Param("businessId") String businessId,
      @Param("processDefinitionId") String processDefinitionId,
      @Param("tenantId") String tenantId);

  void insertBusinessId(@Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  void updateBusinessId(@Param("partitionId") int partitionId, @Param("row") BusinessIdRow row);

  void deleteBusinessId(@Param("partitionId") int partitionId, @Param("key") BusinessIdKey key);

  // CAM_RU_PROCESS_DEFINITION

  @Nullable ProcessDefinitionRow selectProcessDefinition(
      @Param("partitionId") int partitionId, @Param("key") long processDefinitionKey);

  /** The processes with the given id and version, ordered by key. */
  List<ProcessDefinitionRow> selectProcessDefinitionsByVersion(
      @Param("partitionId") int partitionId,
      @Param("tenantId") String tenantId,
      @Param("bpmnProcessId") String bpmnProcessId,
      @Param("version") int version);

  /** At most {@code limit} processes with a key greater than the given one, ordered by key. */
  List<ProcessDefinitionRow> selectProcessDefinitionsAfter(
      @Param("partitionId") int partitionId,
      @Param("afterKey") long afterKey,
      @Param("limit") int limit);

  void insertProcessDefinition(
      @Param("partitionId") int partitionId, @Param("row") ProcessDefinitionRow row);

  void updateProcessDefinition(
      @Param("partitionId") int partitionId, @Param("row") ProcessDefinitionRow row);

  void deleteProcessDefinition(
      @Param("partitionId") int partitionId, @Param("key") long processDefinitionKey);

  // CAM_RU_PROCESS_VERSION

  @Nullable ProcessVersionRow selectProcessVersion(
      @Param("partitionId") int partitionId, @Param("key") ProcessIdKey key);

  /** All version infos of the partition, ordered by tenant and process id. */
  List<ProcessVersionRow> selectProcessVersions(@Param("partitionId") int partitionId);

  void insertProcessVersion(
      @Param("partitionId") int partitionId, @Param("row") ProcessVersionRow row);

  void updateProcessVersion(
      @Param("partitionId") int partitionId, @Param("row") ProcessVersionRow row);

  void deleteProcessVersion(@Param("partitionId") int partitionId, @Param("key") ProcessIdKey key);

  // CAM_RU_PROCESS_DIGEST

  @Nullable ProcessDigestRow selectProcessDigest(
      @Param("partitionId") int partitionId, @Param("key") ProcessIdKey key);

  void insertProcessDigest(
      @Param("partitionId") int partitionId, @Param("row") ProcessDigestRow row);

  void updateProcessDigest(
      @Param("partitionId") int partitionId, @Param("row") ProcessDigestRow row);

  void deleteProcessDigest(@Param("partitionId") int partitionId, @Param("key") ProcessIdKey key);

  // CAM_RU_PROCESS_BY_DEPLOYMENT

  @Nullable ProcessByDeploymentRow selectProcessByDeployment(
      @Param("partitionId") int partitionId, @Param("key") ProcessByDeploymentKey key);

  void insertProcessByDeployment(
      @Param("partitionId") int partitionId, @Param("row") ProcessByDeploymentRow row);

  void updateProcessByDeployment(
      @Param("partitionId") int partitionId, @Param("row") ProcessByDeploymentRow row);

  void deleteProcessByDeployment(
      @Param("partitionId") int partitionId, @Param("key") ProcessByDeploymentKey key);

  // CAM_RU_PROCESS_BY_VERSION_TAG

  @Nullable ProcessByVersionTagRow selectProcessByVersionTag(
      @Param("partitionId") int partitionId, @Param("key") ProcessByVersionTagKey key);

  void insertProcessByVersionTag(
      @Param("partitionId") int partitionId, @Param("row") ProcessByVersionTagRow row);

  void updateProcessByVersionTag(
      @Param("partitionId") int partitionId, @Param("row") ProcessByVersionTagRow row);

  void deleteProcessByVersionTag(
      @Param("partitionId") int partitionId, @Param("key") ProcessByVersionTagKey key);

  // CAM_RU_PENDING_PROCESS_DELETION

  @Nullable PendingProcessDeletionRow selectPendingProcessDeletion(
      @Param("partitionId") int partitionId, @Param("key") PendingProcessDeletionRow key);

  /** The pending deletions of the process definition, ordered by pending partition. */
  List<PendingProcessDeletionRow> selectPendingProcessDeletions(
      @Param("partitionId") int partitionId,
      @Param("processDefinitionKey") long processDefinitionKey);

  void insertPendingProcessDeletion(
      @Param("partitionId") int partitionId, @Param("row") PendingProcessDeletionRow row);

  void updatePendingProcessDeletion(
      @Param("partitionId") int partitionId, @Param("row") PendingProcessDeletionRow row);

  void deletePendingProcessDeletion(
      @Param("partitionId") int partitionId, @Param("key") PendingProcessDeletionRow key);

  // CAM_RU_PARTITION_FLAG

  @Nullable String selectPartitionFlag(
      @Param("partitionId") int partitionId, @Param("key") String flagName);

  void insertPartitionFlag(@Param("partitionId") int partitionId, @Param("row") String flagName);

  void updatePartitionFlag(@Param("partitionId") int partitionId, @Param("row") String flagName);

  void deletePartitionFlag(@Param("partitionId") int partitionId, @Param("key") String flagName);

  /**
   * An element instance: the serialized {@link ElementInstance} plus the columns derived from it
   * for queries. The root process instance key is the process instance key for a root process
   * instance, and for instances created before the record carried a root process instance key.
   */
  record ElementInstanceRow(
      long key,
      long parentKey,
      long processInstanceKey,
      long rootProcessInstanceKey,
      long processDefinitionKey,
      String tenantId,
      String bpmnProcessId,
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

  /** The key of an entry of the business id index: all its columns but the root key. */
  record BusinessIdKey(
      String businessId, String processDefinitionId, String tenantId, long processInstanceKey) {}

  /** An entry of the business id index. */
  record BusinessIdRow(
      String businessId,
      String processDefinitionId,
      String tenantId,
      long processInstanceKey,
      long rootProcessInstanceKey) {

    BusinessIdKey key() {
      return new BusinessIdKey(businessId, processDefinitionId, tenantId, processInstanceKey);
    }
  }

  /**
   * A deployed process: the serialized {@code PersistedProcess} plus the columns derived from it
   * for queries. The version tag is null if the process has none.
   */
  record ProcessDefinitionRow(
      long processDefinitionKey,
      String tenantId,
      String bpmnProcessId,
      int version,
      @Nullable String versionTag,
      long deploymentKey,
      String resourceName,
      short state,
      byte[] payload) {}

  /** The key of the rows that exist once per process id of a tenant. */
  record ProcessIdKey(String tenantId, String bpmnProcessId) {}

  /** The serialized {@code VersionInfo} of a process id, plus columns derived from it. */
  record ProcessVersionRow(
      String tenantId,
      String bpmnProcessId,
      long highestVersion,
      long latestVersion,
      byte[] payload) {

    ProcessIdKey key() {
      return new ProcessIdKey(tenantId, bpmnProcessId);
    }
  }

  /** The checksum of the latest version of a process id. */
  record ProcessDigestRow(String tenantId, String bpmnProcessId, byte[] digest) {

    ProcessIdKey key() {
      return new ProcessIdKey(tenantId, bpmnProcessId);
    }
  }

  record ProcessByDeploymentKey(String tenantId, String bpmnProcessId, long deploymentKey) {}

  /** The process of a process id deployed by a deployment. */
  record ProcessByDeploymentRow(
      String tenantId, String bpmnProcessId, long deploymentKey, long processDefinitionKey) {

    ProcessByDeploymentKey key() {
      return new ProcessByDeploymentKey(tenantId, bpmnProcessId, deploymentKey);
    }
  }

  record ProcessByVersionTagKey(String tenantId, String bpmnProcessId, String versionTag) {}

  /** The highest version of a process id with a version tag. */
  record ProcessByVersionTagRow(
      String tenantId, String bpmnProcessId, String versionTag, long processDefinitionKey) {

    ProcessByVersionTagKey key() {
      return new ProcessByVersionTagKey(tenantId, bpmnProcessId, versionTag);
    }
  }

  /** A partition that still owes a drain report for a process definition; its own key. */
  record PendingProcessDeletionRow(long processDefinitionKey, int pendingPartitionId) {}
}
