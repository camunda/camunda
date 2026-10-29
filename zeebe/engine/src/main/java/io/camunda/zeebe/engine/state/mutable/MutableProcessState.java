/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;

public interface MutableProcessState extends ProcessState {

  void putDeployment(DeploymentRecord deploymentRecord);

  void putLatestVersionDigest(final ProcessRecord processRecord);

  void putProcess(long key, ProcessRecord value);

  void storeProcessDefinitionKeyByProcessIdAndDeploymentKey(final ProcessRecord processRecord);

  void storeProcessDefinitionKeyByProcessIdAndVersionTag(final ProcessRecord processRecord);

  /**
   * Updates the state of a process. This method updates both the ColumnFamily and the in memory
   * cache.
   *
   * @param processRecord the record of the process that is updated
   * @param state the new state
   */
  void updateProcessState(final ProcessRecord processRecord, final PersistedProcessState state);

  /**
   * Marks a definition {@link PersistedProcessState#DRAINING}, remembering from the record whether
   * its instances' history must be deleted once drained. Updates both the ColumnFamily and the
   * in-memory cache.
   */
  void markDraining(final ProcessRecord processRecord);

  /**
   * Sets the deployment key of a process that was not previously associated with a deployment. This
   * method updates both the ColumnFamily and the in memory cache. Throws an exception if the
   * process is already associated with another deployment.
   *
   * @param tenantId tenant id of the process that is updated
   * @param processDefinitionKey the key of the process definition that is updated
   * @param deploymentKey the new deployment key
   */
  void setMissingDeploymentKey(
      final String tenantId, final long processDefinitionKey, final long deploymentKey);

  /**
   * Deletes a process from the state and cache
   *
   * @param processRecord the record of the process that is deleted
   * @deprecated only for replaying Process:DELETED v1 events, use {@link
   *     #deleteProcessAndRepointVersionTag(ProcessRecord)} instead
   */
  @Deprecated
  void deleteProcess(final ProcessRecord processRecord);

  /**
   * Deletes a process like {@link #deleteProcess(ProcessRecord)}, but keeps the version tag lookup
   * of other versions with the same tag: if it points at the deleted process, it is re-pointed to
   * the latest remaining version with that tag, otherwise it is left untouched.
   *
   * @param processRecord the record of the process that is deleted
   */
  void deleteProcessAndRepointVersionTag(final ProcessRecord processRecord);

  /**
   * Records that the given partition still owes a drain report for the given process definition.
   */
  void addPendingDeletion(long processDefinitionKey, int partitionId);

  /** Clears the given partition's outstanding drain report for the given process definition. */
  void removePendingDeletion(long processDefinitionKey, int partitionId);

  /**
   * Restores a process definition that is resting in {@link PersistedProcessState#PENDING_DELETION}
   * back to {@link PersistedProcessState#ACTIVE}. Updates both the by-key ColumnFamily and the
   * in-memory cache; the by-version copy is intentionally left untouched, as it is only used to
   * resolve the by-key definition key (see {@code DbProcessState#lookupPersistenceState}).
   *
   * <p>This is a no-op if the definition no longer exists, or is no longer {@code PENDING_DELETION}
   * (for example, because it was legitimately deleted or already restored since the restore was
   * scheduled), so it is safe to apply even if state has moved on.
   *
   * @param tenantId tenant id of the process definition to restore
   * @param processDefinitionKey the key of the process definition to restore
   */
  void restoreFromPendingDeletion(final String tenantId, final long processDefinitionKey);

  /**
   * Records that the startup cleanup which restores stray resting {@code PENDING_DELETION} process
   * definitions has run on this partition, so it is not scheduled again on a later recovery.
   */
  void markProcessDefinitionRestoreAsRun();
}
