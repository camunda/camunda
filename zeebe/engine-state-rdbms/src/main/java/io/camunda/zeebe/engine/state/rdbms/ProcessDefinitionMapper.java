/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis mapper of the process definition tables, see {@code
 * db/mybatis/engine-state/ProcessDefinitionMapper.xml}. Every statement is scoped to one partition.
 *
 * <p>The rows are immutable snapshots, as the {@link io.camunda.zeebe.db.impl.jdbc.EntityCache}
 * requires: payloads are serialized msgpack and must not be modified.
 */
interface ProcessDefinitionMapper {

  // CAM_RU_PROCESS_DEFINITION

  @Nullable ProcessDefinitionRow selectProcessDefinition(
      @Param("partitionId") int partitionId, @Param("key") long processDefinitionKey);

  /** The processes with the given id and version, ordered by key. */
  List<ProcessDefinitionRow> selectProcessDefinitionsByVersion(
      @Param("partitionId") int partitionId,
      @Param("tenantId") String tenantId,
      @Param("bpmnProcessId") String bpmnProcessId,
      @Param("version") int version);

  /**
   * The processes of the id that the deployment lookup points at, ordered by key; at most one, as
   * the lookup is moved and not copied.
   */
  List<ProcessDefinitionRow> selectProcessDefinitionsByDeploymentLookup(
      @Param("partitionId") int partitionId,
      @Param("tenantId") String tenantId,
      @Param("bpmnProcessId") String bpmnProcessId,
      @Param("deploymentKey") long deploymentKey);

  /** Like {@link #selectProcessDefinitionsByDeploymentLookup}, for the version tag lookup. */
  List<ProcessDefinitionRow> selectProcessDefinitionsByVersionTagLookup(
      @Param("partitionId") int partitionId,
      @Param("tenantId") String tenantId,
      @Param("bpmnProcessId") String bpmnProcessId,
      @Param("versionTag") String versionTag);

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

  /**
   * A deployed process: the serialized {@code PersistedProcess} plus the columns derived from it
   * for queries. The version tag is null if the process has none.
   *
   * <p>The lookup columns are not derived from the payload. They stand for the entries of the
   * RocksDB state's lookups by deployment key and by version tag, are set and cleared where it
   * writes and deletes those entries, and are indexed. They can differ from the deployment key and
   * the version tag: processes deployed before 8.6 have no deployment lookup, and a version tag
   * lookup is removed even if other versions have the tag.
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
      @Nullable Long deploymentKeyLookup,
      @Nullable String versionTagLookup,
      byte[] payload) {

    ProcessDefinitionRow withLookups(
        final @Nullable Long newDeploymentKeyLookup, final @Nullable String newVersionTagLookup) {
      return new ProcessDefinitionRow(
          processDefinitionKey,
          tenantId,
          bpmnProcessId,
          version,
          versionTag,
          deploymentKey,
          resourceName,
          state,
          newDeploymentKeyLookup,
          newVersionTagLookup,
          payload);
    }
  }

  /** The key of the rows that exist once per process id of a tenant. */
  record ProcessIdKey(String tenantId, String bpmnProcessId) {}

  /**
   * The serialized {@code VersionInfo} of a process id, plus columns derived from it, and the
   * checksum of the latest version. The digest is null if there is none, which is not the same as
   * not having a version info.
   */
  record ProcessVersionRow(
      String tenantId,
      String bpmnProcessId,
      long highestVersion,
      long latestVersion,
      byte @Nullable [] digest,
      byte[] payload) {

    ProcessIdKey key() {
      return new ProcessIdKey(tenantId, bpmnProcessId);
    }

    ProcessVersionRow withDigest(final byte @Nullable [] newDigest) {
      return new ProcessVersionRow(
          tenantId, bpmnProcessId, highestVersion, latestVersion, newDigest, payload);
    }
  }

  /** A partition that still owes a drain report for a process definition; its own key. */
  record PendingProcessDeletionRow(long processDefinitionKey, int pendingPartitionId) {}
}
