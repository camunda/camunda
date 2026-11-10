/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.engine.state.deployment.VersionManager.ResourceVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState.PersistedProcessVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Persistence of the process state: reads and writes of the stored processes, their lookups,
 * versions, digests and pending deletions, without the domain rules of {@link ProcessStateImpl}
 * (version and digest bookkeeping on deployment and deletion, state transitions, re-pointing the
 * version tag lookup, caching the deployed processes).
 *
 * <p>The {@link PersistedProcessLoader} reads load the processes for the {@link
 * ProcessDefinitionCache}; the state classes also read them to change and update them. Unless
 * documented otherwise, returned objects may be backed by buffers that the store reuses on its next
 * read: callers must not keep them across another read of the store.
 */
public interface ProcessStore extends PersistedProcessLoader {

  /** What {@link #findProcessKeyByVersionTag} returns if no process has the version tag. */
  long NO_PROCESS = -1L;

  /** Inserts or overwrites the process of the record with the given key. */
  void putProcess(long processDefinitionKey, ProcessRecord processRecord);

  /** Updates the existing process. */
  void updateProcess(String tenantId, PersistedProcess process);

  /**
   * Updates the copy of the process that a store may keep to find it by id and version, if it
   * exists. Such a copy isn't updated with the process otherwise.
   */
  void updateProcessCopyByVersion(String tenantId, PersistedProcess process);

  /**
   * Deletes the existing process of the record, with its copy by id and version and its lookup by
   * deployment key, if they exist.
   */
  void deleteProcess(ProcessRecord processRecord);

  void putDigest(String tenantId, DirectBuffer bpmnProcessId, DirectBuffer digest);

  @Nullable DirectBuffer getDigest(String tenantId, DirectBuffer bpmnProcessId);

  /** Deletes the digest of the process id, if it exists. */
  void deleteDigest(String tenantId, DirectBuffer bpmnProcessId);

  void putProcessKeyByDeploymentKey(
      String tenantId, DirectBuffer bpmnProcessId, long deploymentKey, long processDefinitionKey);

  void putProcessKeyByVersionTag(
      String tenantId, DirectBuffer bpmnProcessId, String versionTag, long processDefinitionKey);

  /**
   * @return the key of the process the version tag points to, or {@link #NO_PROCESS}
   */
  long findProcessKeyByVersionTag(String tenantId, DirectBuffer bpmnProcessId, String versionTag);

  /** Deletes the lookup of the version tag, if it exists. */
  void deleteProcessKeyByVersionTag(String tenantId, DirectBuffer bpmnProcessId, String versionTag);

  void addVersion(String bpmnProcessId, long version, String tenantId);

  /** Removes a version of a process id that has versions. */
  void removeVersion(String bpmnProcessId, long version, String tenantId);

  long getLatestVersion(DirectBuffer bpmnProcessId, String tenantId);

  long getLatestVersion(String bpmnProcessId, String tenantId);

  long getHighestVersion(String bpmnProcessId, String tenantId);

  Optional<Integer> findVersionBefore(String bpmnProcessId, long version, String tenantId);

  List<Long> getKnownVersions(String bpmnProcessId, String tenantId);

  /** Visits the version infos of all process ids. */
  void forEachVersion(ResourceVisitor visitor);

  /** Drops the version infos that the store keeps in memory. */
  void clearVersions();

  void addPendingDeletion(long processDefinitionKey, int partitionId);

  void removePendingDeletion(long processDefinitionKey, int partitionId);

  boolean hasPendingDeletion(long processDefinitionKey, int partitionId);

  boolean hasPendingDeletion(long processDefinitionKey);

  Set<Integer> getPendingDeletionPartitions(long processDefinitionKey);

  void markProcessDefinitionRestoreAsRun();

  boolean hasRunProcessDefinitionRestore();

  /** See {@link ProcessState#forEachProcess}. */
  void forEachProcess(@Nullable ProcessIdentifier previousProcess, PersistedProcessVisitor visitor);
}
