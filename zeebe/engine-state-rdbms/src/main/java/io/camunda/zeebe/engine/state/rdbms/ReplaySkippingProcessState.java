/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Leaves the process definition rows alone while the replay applies events whose changes the
 * database already contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the
 * mutators do nothing, and the reads see the rows in the database's later state. Otherwise, every
 * call is delegated. The in-memory cache methods are always delegated, as they write no rows.
 */
@NullMarked
final class ReplaySkippingProcessState implements MutableProcessState {

  private final MutableProcessState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingProcessState(
      final MutableProcessState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  public void putDeployment(final DeploymentRecord deploymentRecord) {
    if (!skip()) {
      delegate.putDeployment(deploymentRecord);
    }
  }

  @Override
  public void putLatestVersionDigest(final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.putLatestVersionDigest(processRecord);
    }
  }

  @Override
  public void putProcess(final long key, final ProcessRecord value) {
    if (!skip()) {
      delegate.putProcess(key, value);
    }
  }

  @Override
  public void storeProcessDefinitionKeyByProcessIdAndDeploymentKey(
      final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.storeProcessDefinitionKeyByProcessIdAndDeploymentKey(processRecord);
    }
  }

  @Override
  public void storeProcessDefinitionKeyByProcessIdAndVersionTag(final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.storeProcessDefinitionKeyByProcessIdAndVersionTag(processRecord);
    }
  }

  @Override
  public void updateProcessState(
      final ProcessRecord processRecord, final PersistedProcessState state) {
    if (!skip()) {
      delegate.updateProcessState(processRecord, state);
    }
  }

  @Override
  public void markDraining(final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.markDraining(processRecord);
    }
  }

  @Override
  public void setMissingDeploymentKey(
      final String tenantId, final long processDefinitionKey, final long deploymentKey) {
    if (!skip()) {
      delegate.setMissingDeploymentKey(tenantId, processDefinitionKey, deploymentKey);
    }
  }

  @Override
  @Deprecated
  public void deleteProcess(final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.deleteProcess(processRecord);
    }
  }

  @Override
  public void deleteProcessAndRepointVersionTag(final ProcessRecord processRecord) {
    if (!skip()) {
      delegate.deleteProcessAndRepointVersionTag(processRecord);
    }
  }

  @Override
  public void addPendingDeletion(final long processDefinitionKey, final int partitionId) {
    if (!skip()) {
      delegate.addPendingDeletion(processDefinitionKey, partitionId);
    }
  }

  @Override
  public void removePendingDeletion(final long processDefinitionKey, final int partitionId) {
    if (!skip()) {
      delegate.removePendingDeletion(processDefinitionKey, partitionId);
    }
  }

  @Override
  public void restoreFromPendingDeletion(final String tenantId, final long processDefinitionKey) {
    if (!skip()) {
      delegate.restoreFromPendingDeletion(tenantId, processDefinitionKey);
    }
  }

  @Override
  public void markProcessDefinitionRestoreAsRun() {
    if (!skip()) {
      delegate.markProcessDefinitionRestoreAsRun();
    }
  }

  @Override
  public @Nullable DeployedProcess getLatestProcessVersionByProcessId(
      final DirectBuffer processId, final String tenantId) {
    return delegate.getLatestProcessVersionByProcessId(processId, tenantId);
  }

  @Override
  public @Nullable DeployedProcess getLatestActiveProcessVersionByProcessId(
      final DirectBuffer processId, final String tenantId) {
    return delegate.getLatestActiveProcessVersionByProcessId(processId, tenantId);
  }

  @Override
  public @Nullable DeployedProcess getProcessByProcessIdAndVersion(
      final DirectBuffer processId, final int version, final String tenantId) {
    return delegate.getProcessByProcessIdAndVersion(processId, version, tenantId);
  }

  @Override
  public @Nullable DeployedProcess getProcessByProcessIdAndVersionTag(
      final DirectBuffer processId, final String versionTag, final String tenantId) {
    return delegate.getProcessByProcessIdAndVersionTag(processId, versionTag, tenantId);
  }

  @Override
  public @Nullable DeployedProcess getProcessByProcessIdAndDeploymentKey(
      final DirectBuffer processId, final long deploymentKey, final String tenantId) {
    return delegate.getProcessByProcessIdAndDeploymentKey(processId, deploymentKey, tenantId);
  }

  @Override
  public @Nullable DeployedProcess getProcessByKeyAndTenant(final long key, final String tenantId) {
    return delegate.getProcessByKeyAndTenant(key, tenantId);
  }

  @Override
  public @Nullable DirectBuffer getLatestVersionDigest(
      final DirectBuffer processId, final String tenantId) {
    return delegate.getLatestVersionDigest(processId, tenantId);
  }

  @Override
  public int getLatestProcessVersion(final String bpmnProcessId, final String tenantId) {
    return delegate.getLatestProcessVersion(bpmnProcessId, tenantId);
  }

  @Override
  public int getNextProcessVersion(final String bpmnProcessId, final String tenantId) {
    return delegate.getNextProcessVersion(bpmnProcessId, tenantId);
  }

  @Override
  public Optional<Integer> findProcessVersionBefore(
      final String bpmnProcessId, final long version, final String tenantId) {
    return delegate.findProcessVersionBefore(bpmnProcessId, version, tenantId);
  }

  @Override
  public List<Long> getKnownProcessVersions(final String bpmnProcessId, final String tenantId) {
    return delegate.getKnownProcessVersions(bpmnProcessId, tenantId);
  }

  @Override
  public <T extends ExecutableFlowElement> @Nullable T getFlowElement(
      final long processDefinitionKey,
      final String tenantId,
      final DirectBuffer elementId,
      final Class<T> elementType) {
    return delegate.getFlowElement(processDefinitionKey, tenantId, elementId, elementType);
  }

  @Override
  public void clearCache() {
    delegate.clearCache();
  }

  @Override
  public void cacheProcess(
      final long processDefinitionKey,
      final String tenantId,
      final ExecutableProcess executableProcess) {
    delegate.cacheProcess(processDefinitionKey, tenantId, executableProcess);
  }

  @Override
  public void forEachProcess(
      final @Nullable ProcessIdentifier previousProcess, final PersistedProcessVisitor visitor) {
    delegate.forEachProcess(previousProcess, visitor);
  }

  @Override
  public void forEachProcessWithLatestVersion(final PersistedProcessVisitor visitor) {
    delegate.forEachProcessWithLatestVersion(visitor);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey, final int partitionId) {
    return delegate.hasPendingDeletion(processDefinitionKey, partitionId);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey) {
    return delegate.hasPendingDeletion(processDefinitionKey);
  }

  @Override
  public Set<Integer> getPendingDeletionPartitions(final long processDefinitionKey) {
    return delegate.getPendingDeletionPartitions(processDefinitionKey);
  }

  @Override
  public boolean hasRunProcessDefinitionRestore() {
    return delegate.hasRunProcessDefinitionRestore();
  }
}
