/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;

/**
 * The process state, independent of where it is stored: keeps the versions and the digest of a
 * process id with its processes, moves processes through their states, re-points the version tag
 * lookup when a process is deleted, and caches the deployed processes in a {@link
 * ProcessDefinitionCache}. The {@link ProcessStore} persists it.
 */
public final class ProcessStateImpl implements MutableProcessState {

  private final ProcessRecord processRecordForDeployments = new ProcessRecord();
  private final ProcessStore store;
  private final ProcessDefinitionCache processCache;

  /**
   * @param transactionContext the context of the transactions the store reads and writes in; the
   *     process cache follows their outcome
   */
  public ProcessStateImpl(
      final ProcessStore store,
      final EngineConfiguration config,
      final BpmnTransformer transformer,
      final TransactionContext transactionContext,
      final MeterRegistry meterRegistry) {
    this.store = store;
    processCache =
        new ProcessDefinitionCache(config, transformer, store, transactionContext, meterRegistry);
  }

  @Override
  public void putDeployment(final DeploymentRecord deploymentRecord) {
    for (final ProcessMetadata metadata : deploymentRecord.processesMetadata()) {
      for (final DeploymentResource resource : deploymentRecord.getResources()) {
        if (resource.getResourceName().equals(metadata.getResourceName())) {
          processRecordForDeployments.reset();
          processRecordForDeployments.wrap(metadata, resource.getResource());
          putProcess(metadata.getKey(), processRecordForDeployments);
        }
      }
    }
  }

  @Override
  public void putLatestVersionDigest(final ProcessRecord processRecord) {
    store.putDigest(
        processRecord.getTenantId(),
        processRecord.getBpmnProcessIdBuffer(),
        processRecord.getChecksumBuffer());
  }

  @Override
  public void putProcess(final long key, final ProcessRecord processRecord) {
    store.putProcess(key, processRecord);
    store.addVersion(
        processRecord.getBpmnProcessId(), processRecord.getVersion(), processRecord.getTenantId());
    putLatestVersionDigest(processRecord);
  }

  @Override
  public void storeProcessDefinitionKeyByProcessIdAndDeploymentKey(
      final ProcessRecord processRecord) {
    store.putProcessKeyByDeploymentKey(
        processRecord.getTenantId(),
        processRecord.getBpmnProcessIdBuffer(),
        processRecord.getDeploymentKey(),
        processRecord.getProcessDefinitionKey());
  }

  @Override
  public void storeProcessDefinitionKeyByProcessIdAndVersionTag(final ProcessRecord processRecord) {
    final var versionTag = processRecord.getVersionTag();
    if (!versionTag.isBlank()) {
      store.putProcessKeyByVersionTag(
          processRecord.getTenantId(),
          processRecord.getBpmnProcessIdBuffer(),
          versionTag,
          processRecord.getProcessDefinitionKey());
    }
  }

  @Override
  public void updateProcessState(
      final ProcessRecord processRecord, final PersistedProcessState state) {
    final var tenantId = processRecord.getTenantId();
    final var process = requireProcess(tenantId, processRecord.getProcessDefinitionKey());
    process.setState(state);
    store.updateProcess(tenantId, process);
    processCache.cache(process);
  }

  @Override
  public void markDraining(final ProcessRecord processRecord) {
    final var tenantId = processRecord.getTenantId();
    final var process = requireProcess(tenantId, processRecord.getProcessDefinitionKey());
    process.setState(PersistedProcessState.DRAINING);
    process.setDeleteHistory(processRecord.isDeleteHistory());
    store.updateProcess(tenantId, process);
    processCache.cache(process);
  }

  @Override
  public void setMissingDeploymentKey(
      final String tenantId, final long processDefinitionKey, final long deploymentKey) {
    final var process = requireProcess(tenantId, processDefinitionKey);

    if (process.getDeploymentKey() == deploymentKey) {
      return;
    }

    if (process.hasDeploymentKey()) {
      throw new IllegalStateException(
          String.format(
              "Expected to set deployment key '%d' for process with key '%d' but process already has deployment key '%d'.",
              deploymentKey, processDefinitionKey, process.getDeploymentKey()));
    }

    process.setDeploymentKey(deploymentKey);
    store.updateProcess(tenantId, process);
    store.putProcessKeyByDeploymentKey(
        tenantId, process.getBpmnProcessId(), deploymentKey, processDefinitionKey);
    processCache.invalidate(
        tenantId,
        process.getBpmnProcessId(),
        processDefinitionKey,
        deploymentKey,
        process.getVersion());
  }

  @Override
  public void deleteProcess(final ProcessRecord processRecord) {
    deleteProcessExceptVersionTag(processRecord);

    // processes without a version tag have no lookup
    final var versionTag = processRecord.getVersionTag();
    if (!versionTag.isBlank()) {
      store.deleteProcessKeyByVersionTag(
          processRecord.getTenantId(), processRecord.getBpmnProcessIdBuffer(), versionTag);
    }
  }

  @Override
  public void deleteProcessAndRepointVersionTag(final ProcessRecord processRecord) {
    deleteProcessExceptVersionTag(processRecord);
    updateVersionTagOnDelete(processRecord);
  }

  @Override
  public void addPendingDeletion(final long processDefinitionKey, final int partitionId) {
    store.addPendingDeletion(processDefinitionKey, partitionId);
  }

  @Override
  public void removePendingDeletion(final long processDefinitionKey, final int partitionId) {
    store.removePendingDeletion(processDefinitionKey, partitionId);
  }

  @Override
  public void restoreFromPendingDeletion(final String tenantId, final long processDefinitionKey) {
    final var process = store.findProcessByKey(tenantId, processDefinitionKey);
    if (process == null || process.getState() != PersistedProcessState.PENDING_DELETION) {
      // Already restored, deleted, or never stray to begin with - nothing to do. This keeps the
      // restore idempotent against state that may have moved on since it was scheduled.
      return;
    }

    process.setState(PersistedProcessState.ACTIVE);
    store.updateProcess(tenantId, process);
    store.updateProcessCopyByVersion(tenantId, process);
    processCache.cache(process);
  }

  @Override
  public void markProcessDefinitionRestoreAsRun() {
    store.markProcessDefinitionRestoreAsRun();
  }

  @Override
  public boolean hasRunProcessDefinitionRestore() {
    return store.hasRunProcessDefinitionRestore();
  }

  @Override
  public DeployedProcess getLatestProcessVersionByProcessId(
      final DirectBuffer processIdBuffer, final String tenantId) {
    final long latestVersion = store.getLatestVersion(processIdBuffer, tenantId);
    return processCache.getProcessByProcessIdAndVersion(tenantId, processIdBuffer, latestVersion);
  }

  /**
   * {@inheritDoc}
   *
   * <p>When the highest version is active (see {@link DeployedProcess#isActive()}), it is returned
   * immediately without loading known versions. The reverse scan only runs when that version is not
   * active, and it skips {@code version >= latestVersion} because that version was already checked.
   */
  @Override
  public DeployedProcess getLatestActiveProcessVersionByProcessId(
      final DirectBuffer processIdBuffer, final String tenantId) {
    DeployedProcess process = getLatestProcessVersionByProcessId(processIdBuffer, tenantId);
    if (process == null) {
      return null;
    }
    if (process.isActive()) {
      return process;
    }

    final int latestVersion = process.getVersion();
    final var knownVersions = getKnownProcessVersions(bufferAsString(processIdBuffer), tenantId);
    for (int i = knownVersions.size() - 1; i >= 0; i--) {
      final int version = knownVersions.get(i).intValue();
      if (version >= latestVersion) {
        continue;
      }
      process = getProcessByProcessIdAndVersion(processIdBuffer, version, tenantId);
      if (process != null && process.isActive()) {
        return process;
      }
    }
    return null;
  }

  @Override
  public DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer processId, final int version, final String tenantId) {
    return processCache.getProcessByProcessIdAndVersion(tenantId, processId, version);
  }

  @Override
  public DeployedProcess getProcessByProcessIdAndVersionTag(
      final DirectBuffer processId, final String versionTag, final String tenantId) {
    final long processDefinitionKey =
        store.findProcessKeyByVersionTag(tenantId, processId, versionTag);
    if (processDefinitionKey == ProcessStore.NO_PROCESS) {
      return null;
    }
    return getProcessByKeyAndTenant(processDefinitionKey, tenantId);
  }

  @Override
  public DeployedProcess getProcessByProcessIdAndDeploymentKey(
      final DirectBuffer processIdBuffer, final long deploymentKey, final String tenantId) {
    return processCache.getProcessByProcessIdAndDeploymentKey(
        tenantId, processIdBuffer, deploymentKey);
  }

  @Override
  public DeployedProcess getProcessByKeyAndTenant(final long key, final String tenantId) {
    return processCache.getProcessByKey(tenantId, key);
  }

  @Override
  public DirectBuffer getLatestVersionDigest(
      final DirectBuffer processIdBuffer, final String tenantId) {
    return store.getDigest(tenantId, processIdBuffer);
  }

  @Override
  public int getLatestProcessVersion(final String bpmnProcessId, final String tenantId) {
    return (int) store.getLatestVersion(bpmnProcessId, tenantId);
  }

  @Override
  public int getNextProcessVersion(final String bpmnProcessId, final String tenantId) {
    return (int) store.getHighestVersion(bpmnProcessId, tenantId) + 1;
  }

  @Override
  public Optional<Integer> findProcessVersionBefore(
      final String bpmnProcessId, final long version, final String tenantId) {
    return store.findVersionBefore(bpmnProcessId, version, tenantId);
  }

  @Override
  public List<Long> getKnownProcessVersions(final String bpmnProcessId, final String tenantId) {
    return store.getKnownVersions(bpmnProcessId, tenantId);
  }

  @Override
  public <T extends ExecutableFlowElement> T getFlowElement(
      final long processDefinitionKey,
      final String tenantId,
      final DirectBuffer elementId,
      final Class<T> elementType) {

    final var deployedProcess = getProcessByKeyAndTenant(processDefinitionKey, tenantId);
    if (deployedProcess == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a process deployed with key '%d' but not found.",
              processDefinitionKey));
    }

    final var process = deployedProcess.getProcess();
    final var element = process.getElementById(elementId, elementType);
    if (element == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find a flow element with id '%s' in process with key '%d' but not found.",
              bufferAsString(elementId), processDefinitionKey));
    }

    return element;
  }

  @Override
  public void clearCache() {
    processCache.invalidateAll();
    store.clearVersions();
  }

  @Override
  public void cacheProcess(
      final long processDefinitionKey,
      final String tenantId,
      final ExecutableProcess executableProcess) {
    processCache.cache(tenantId, processDefinitionKey, executableProcess);
  }

  @Override
  public void forEachProcess(
      final ProcessIdentifier previousProcess, final PersistedProcessVisitor visitor) {
    store.forEachProcess(previousProcess, visitor);
  }

  @Override
  public void forEachProcessWithLatestVersion(final PersistedProcessVisitor visitor) {
    store.forEachVersion(
        (processId, tenantId, versionInfo) -> {
          if (versionInfo.getLatestVersion() > 0) {
            final var process =
                getProcessByProcessIdAndVersion(
                    processId, versionInfo.getLatestVersion().intValue(), tenantId);
            return visitor.visit(process.getPersistedProcess());
          } else {
            return true;
          }
        });
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey, final int partitionId) {
    return store.hasPendingDeletion(processDefinitionKey, partitionId);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey) {
    return store.hasPendingDeletion(processDefinitionKey);
  }

  @Override
  public Set<Integer> getPendingDeletionPartitions(final long processDefinitionKey) {
    return store.getPendingDeletionPartitions(processDefinitionKey);
  }

  private PersistedProcess requireProcess(final String tenantId, final long processDefinitionKey) {
    final var process = store.findProcessByKey(tenantId, processDefinitionKey);
    if (process == null) {
      throw new IllegalStateException(
          "Expected to find process %d of tenant '%s', but it does not exist"
              .formatted(processDefinitionKey, tenantId));
    }
    return process;
  }

  private void deleteProcessExceptVersionTag(final ProcessRecord processRecord) {
    store.deleteProcess(processRecord);

    processCache.invalidate(
        processRecord.getTenantId(),
        processRecord.getBpmnProcessIdBuffer(),
        processRecord.getProcessDefinitionKey(),
        processRecord.getDeploymentKey(),
        processRecord.getVersion());

    final long latestVersion =
        store.getLatestVersion(processRecord.getBpmnProcessId(), processRecord.getTenantId());
    if (latestVersion == processRecord.getVersion()) {
      // As we don't set the digest to the digest of the previous there is a chance it does not
      // exist. This happens when deleting the latest version two times in a row.
      store.deleteDigest(processRecord.getTenantId(), processRecord.getBpmnProcessIdBuffer());
    }

    store.removeVersion(
        processRecord.getBpmnProcessId(), processRecord.getVersion(), processRecord.getTenantId());
  }

  /**
   * Points the version tag lookup at the latest remaining version with the deleted process' tag, if
   * it pointed at the deleted process, or removes it if no other version has that tag.
   */
  private void updateVersionTagOnDelete(final ProcessRecord processRecord) {
    final var deletedVersionTag = processRecord.getVersionTag();
    if (deletedVersionTag.isBlank()) {
      return;
    }
    final var tenantId = processRecord.getTenantId();
    final var processId = processRecord.getBpmnProcessIdBuffer();
    final long indexedKey =
        store.findProcessKeyByVersionTag(tenantId, processId, deletedVersionTag);
    if (indexedKey != processRecord.getProcessDefinitionKey()) {
      return;
    }

    // the lookup points to the highest version with the tag, so only lower versions can match
    final var knownVersions = getKnownProcessVersions(processRecord.getBpmnProcessId(), tenantId);
    for (final long version : knownVersions.reversed()) {
      if (version >= processRecord.getVersion()) {
        continue;
      }
      final var candidate = store.findProcessByProcessIdAndVersion(tenantId, processId, version);
      if (candidate != null && deletedVersionTag.equals(candidate.getVersionTag())) {
        store.putProcessKeyByVersionTag(tenantId, processId, deletedVersionTag, candidate.getKey());
        return;
      }
    }
    store.deleteProcessKeyByVersionTag(tenantId, processId, deletedVersionTag);
  }
}
