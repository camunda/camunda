/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.deployment.VersionManager.ResourceVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.PersistedProcessVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.jspecify.annotations.Nullable;

/**
 * Stores the process state in RocksDB column families. Besides the process by key, it keeps a copy
 * by id and version, which is written once on deployment and not updated with the process except by
 * {@link #updateProcessCopyByVersion}, and the versions of the process ids in a {@link
 * VersionManager}.
 *
 * <p>Reads return the column family's value instances, which the next read of the same column
 * family overwrites.
 */
public final class DbProcessStore implements ProcessStore {

  public static final String PROCESS_DEFINITION_RESTORE_KEY_VALUE = "process-definition-restore";
  private static final int DEFAULT_VERSION_VALUE = 0;

  /** [tenant id | process definition key] => process */
  private final ColumnFamily<DbTenantAwareKey<DbLong>, PersistedProcess> processColumnFamily;

  private final DbLong processDefinitionKey;
  private final PersistedProcess persistedProcess;
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessDefinitionKey;
  private final DbForeignKey<DbTenantAwareKey<DbLong>> fkProcessDefinitionKey;

  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>, PersistedProcess>
      processByIdAndVersionColumnFamily;
  private final DbLong processVersion;
  private final DbCompositeKey<DbString, DbLong> idAndVersionKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareProcessIdAndVersionKey;

  private final DbString processId;
  private final DbTenantAwareKey<DbString> tenantAwareProcessId;
  private final DbForeignKey<DbTenantAwareKey<DbString>> fkTenantAwareProcessId;

  private final ColumnFamily<DbForeignKey<DbTenantAwareKey<DbString>>, Digest>
      digestByIdColumnFamily;
  private final Digest digest = new Digest();

  private final DbLong deploymentKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>
      tenantAwareProcessIdAndDeploymentKey;

  /**
   * <b>Note</b>: Will only be filled with entries deployed from 8.6 onwards; previously deployed
   * processes will not have an entry in this column family.
   */
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbLong>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      processDefinitionKeyByProcessIdAndDeploymentKeyColumnFamily;

  private final DbString versionTag;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
      tenantAwareProcessIdAndVersionTagKey;

  /**
   * <b>Note</b>: Will only be filled with processes deployed from 8.6 onwards that have a version
   * tag assigned (which is an optional property).
   */
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbString>>,
          DbForeignKey<DbTenantAwareKey<DbLong>>>
      processDefinitionKeyByProcessIdAndVersionTagColumnFamily;

  private final VersionManager versionManager;

  private final DbInt pendingDeletionPartitionId;
  private final DbCompositeKey<DbLong, DbInt> pendingDeletionKey;

  /** [process definition key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil>
      pendingProcessDeletionsPerPartitionColumnFamily;

  private final DbString processDefinitionRestoreKey;

  /** [process-definition-restore] => [DbNil], set once the startup restore has run */
  private final ColumnFamily<DbString, DbNil> processDefinitionRestoreColumnFamily;

  public DbProcessStore(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processDefinitionKey = new DbLong();
    persistedProcess = new PersistedProcess();
    tenantIdKey = new DbString();
    tenantAwareProcessDefinitionKey =
        new DbTenantAwareKey<>(tenantIdKey, processDefinitionKey, PlacementType.PREFIX);
    fkProcessDefinitionKey =
        new DbForeignKey<>(tenantAwareProcessDefinitionKey, ZbColumnFamilies.PROCESS_CACHE);
    processColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE,
            transactionContext,
            tenantAwareProcessDefinitionKey,
            persistedProcess);

    processId = new DbString();
    processVersion = new DbLong();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processByIdAndVersionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            persistedProcess);

    tenantAwareProcessId = new DbTenantAwareKey<>(tenantIdKey, processId, PlacementType.PREFIX);
    fkTenantAwareProcessId =
        new DbForeignKey<>(
            tenantAwareProcessId,
            ZbColumnFamilies.PROCESS_CACHE_BY_ID_AND_VERSION,
            MatchType.Prefix);
    digestByIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_CACHE_DIGEST_BY_ID,
            transactionContext,
            fkTenantAwareProcessId,
            digest);

    deploymentKey = new DbLong();
    tenantAwareProcessIdAndDeploymentKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(processId, deploymentKey), PlacementType.PREFIX);
    processDefinitionKeyByProcessIdAndDeploymentKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_DEPLOYMENT_KEY,
            transactionContext,
            tenantAwareProcessIdAndDeploymentKey,
            fkProcessDefinitionKey);

    versionTag = new DbString();
    tenantAwareProcessIdAndVersionTagKey =
        new DbTenantAwareKey<>(
            tenantIdKey, new DbCompositeKey<>(processId, versionTag), PlacementType.PREFIX);
    processDefinitionKeyByProcessIdAndVersionTagColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_DEFINITION_KEY_BY_PROCESS_ID_AND_VERSION_TAG,
            transactionContext,
            tenantAwareProcessIdAndVersionTagKey,
            fkProcessDefinitionKey);

    versionManager =
        new VersionManager(
            DEFAULT_VERSION_VALUE, zeebeDb, ZbColumnFamilies.PROCESS_VERSION, transactionContext);

    pendingDeletionPartitionId = new DbInt();
    pendingDeletionKey = new DbCompositeKey<>(processDefinitionKey, pendingDeletionPartitionId);
    pendingProcessDeletionsPerPartitionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_PROCESS_DELETIONS_PER_PARTITION,
            transactionContext,
            pendingDeletionKey,
            DbNil.INSTANCE);

    processDefinitionRestoreKey = new DbString();
    processDefinitionRestoreKey.wrapString(PROCESS_DEFINITION_RESTORE_KEY_VALUE);
    processDefinitionRestoreColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT,
            transactionContext,
            processDefinitionRestoreKey,
            DbNil.INSTANCE);
  }

  @Override
  public void putProcess(final long processDefinitionKey, final ProcessRecord processRecord) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    persistedProcess.wrap(processRecord, processDefinitionKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    processColumnFamily.upsert(tenantAwareProcessDefinitionKey, persistedProcess);

    processId.wrapBuffer(processRecord.getBpmnProcessIdBuffer());
    processVersion.wrapLong(processRecord.getVersion());

    processByIdAndVersionColumnFamily.upsert(tenantAwareProcessIdAndVersionKey, persistedProcess);
  }

  @Override
  public void updateProcess(final String tenantId, final PersistedProcess process) {
    tenantIdKey.wrapString(tenantId);
    processDefinitionKey.wrapLong(process.getKey());
    processColumnFamily.update(tenantAwareProcessDefinitionKey, process);
  }

  @Override
  public void updateProcessCopyByVersion(final String tenantId, final PersistedProcess process) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(process.getBpmnProcessId());
    processVersion.wrapLong(process.getVersion());
    if (processByIdAndVersionColumnFamily.exists(tenantAwareProcessIdAndVersionKey)) {
      processByIdAndVersionColumnFamily.update(tenantAwareProcessIdAndVersionKey, process);
    }
  }

  @Override
  public void deleteProcess(final ProcessRecord processRecord) {
    tenantIdKey.wrapString(processRecord.getTenantId());
    processDefinitionKey.wrapLong(processRecord.getProcessDefinitionKey());
    processId.wrapString(processRecord.getBpmnProcessId());
    processVersion.wrapLong(processRecord.getVersion());
    deploymentKey.wrapLong(processRecord.getDeploymentKey());

    processColumnFamily.deleteExisting(tenantAwareProcessDefinitionKey);
    processByIdAndVersionColumnFamily.deleteExisting(tenantAwareProcessIdAndVersionKey);

    // use deleteIfExists as no entry exists for older records that do not have a deployment key yet
    processDefinitionKeyByProcessIdAndDeploymentKeyColumnFamily.deleteIfExists(
        tenantAwareProcessIdAndDeploymentKey);
  }

  @Override
  public void putDigest(
      final String tenantId, final DirectBuffer bpmnProcessId, final DirectBuffer digest) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    this.digest.set(digest);

    digestByIdColumnFamily.upsert(fkTenantAwareProcessId, this.digest);
  }

  @Override
  public DirectBuffer getDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    final Digest latestDigest = digestByIdColumnFamily.get(fkTenantAwareProcessId);
    return latestDigest == null || digest.get().byteArray() == null ? null : latestDigest.get();
  }

  @Override
  public void deleteDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    digestByIdColumnFamily.deleteIfExists(fkTenantAwareProcessId);
  }

  @Override
  public void putProcessKeyByDeploymentKey(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final long deploymentKey,
      final long processDefinitionKey) {
    tenantIdKey.wrapString(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    processId.wrapBuffer(bpmnProcessId);
    this.deploymentKey.wrapLong(deploymentKey);
    processDefinitionKeyByProcessIdAndDeploymentKeyColumnFamily.upsert(
        tenantAwareProcessIdAndDeploymentKey, fkProcessDefinitionKey);
  }

  @Override
  public void putProcessKeyByVersionTag(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final String versionTag,
      final long processDefinitionKey) {
    tenantIdKey.wrapString(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    processId.wrapBuffer(bpmnProcessId);
    this.versionTag.wrapString(versionTag);
    processDefinitionKeyByProcessIdAndVersionTagColumnFamily.upsert(
        tenantAwareProcessIdAndVersionTagKey, fkProcessDefinitionKey);
  }

  @Override
  public long findProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    this.versionTag.wrapString(versionTag);
    final var foreignKey =
        processDefinitionKeyByProcessIdAndVersionTagColumnFamily.get(
            tenantAwareProcessIdAndVersionTagKey);
    return foreignKey == null ? NO_PROCESS : foreignKey.inner().wrappedKey().getValue();
  }

  @Override
  public void deleteProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    this.versionTag.wrapString(versionTag);
    processDefinitionKeyByProcessIdAndVersionTagColumnFamily.deleteIfExists(
        tenantAwareProcessIdAndVersionTagKey);
  }

  @Override
  public void addVersion(final String bpmnProcessId, final long version, final String tenantId) {
    versionManager.addResourceVersion(bpmnProcessId, version, tenantId);
  }

  @Override
  public void removeVersion(final String bpmnProcessId, final long version, final String tenantId) {
    versionManager.deleteResourceVersion(bpmnProcessId, version, tenantId);
  }

  @Override
  public long getLatestVersion(final DirectBuffer bpmnProcessId, final String tenantId) {
    return versionManager.getLatestResourceVersion(bpmnProcessId, tenantId);
  }

  @Override
  public long getLatestVersion(final String bpmnProcessId, final String tenantId) {
    return versionManager.getLatestResourceVersion(bpmnProcessId, tenantId);
  }

  @Override
  public long getHighestVersion(final String bpmnProcessId, final String tenantId) {
    return versionManager.getHighestResourceVersion(bpmnProcessId, tenantId);
  }

  @Override
  public Optional<Integer> findVersionBefore(
      final String bpmnProcessId, final long version, final String tenantId) {
    return versionManager.findResourceVersionBefore(bpmnProcessId, version, tenantId);
  }

  @Override
  public List<Long> getKnownVersions(final String bpmnProcessId, final String tenantId) {
    return versionManager.getKnownResourceVersions(bpmnProcessId, tenantId);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Visits all process ids: the visitor's result doesn't stop the iteration.
   */
  @Override
  public void forEachVersion(final ResourceVisitor visitor) {
    versionManager.forEachResource(visitor);
  }

  @Override
  public void clearVersions() {
    versionManager.clear();
  }

  @Override
  public void addPendingDeletion(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    pendingDeletionPartitionId.wrapInt(partitionId);
    pendingProcessDeletionsPerPartitionColumnFamily.insert(pendingDeletionKey, DbNil.INSTANCE);
  }

  @Override
  public void removePendingDeletion(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    pendingDeletionPartitionId.wrapInt(partitionId);
    pendingProcessDeletionsPerPartitionColumnFamily.deleteExisting(pendingDeletionKey);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    pendingDeletionPartitionId.wrapInt(partitionId);
    return pendingProcessDeletionsPerPartitionColumnFamily.exists(pendingDeletionKey);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    final var hasPending = new MutableBoolean();
    pendingProcessDeletionsPerPartitionColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        ignored -> {
          hasPending.set(true);
          return false;
        });
    return hasPending.get();
  }

  @Override
  public Set<Integer> getPendingDeletionPartitions(final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    final var partitions = new HashSet<Integer>();
    pendingProcessDeletionsPerPartitionColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        key -> {
          partitions.add(key.second().getValue());
          return true;
        });
    return partitions;
  }

  @Override
  public void markProcessDefinitionRestoreAsRun() {
    processDefinitionRestoreKey.wrapString(PROCESS_DEFINITION_RESTORE_KEY_VALUE);
    processDefinitionRestoreColumnFamily.upsert(processDefinitionRestoreKey, DbNil.INSTANCE);
  }

  @Override
  public boolean hasRunProcessDefinitionRestore() {
    processDefinitionRestoreKey.wrapString(PROCESS_DEFINITION_RESTORE_KEY_VALUE);
    return processDefinitionRestoreColumnFamily.exists(processDefinitionRestoreKey);
  }

  @Override
  public void forEachProcess(
      final ProcessIdentifier previousProcess, final PersistedProcessVisitor visitor) {

    if (previousProcess == null) {
      processColumnFamily.whileTrue((key, value) -> visitor.visit(value));
      return;
    }

    tenantIdKey.wrapString(previousProcess.tenantId());
    processDefinitionKey.wrapLong(previousProcess.processDefinitionKey());
    processColumnFamily.whileTrue(
        tenantAwareProcessDefinitionKey,
        (key, value) -> {
          if (key.tenantKey().toString().equals(previousProcess.tenantId())
              && key.wrappedKey().getValue() == previousProcess.processDefinitionKey()) {
            return true;
          }
          return visitor.visit(value);
        });
  }

  @Override
  public @Nullable PersistedProcess findProcessByKey(
      final String tenantId, final long processDefinitionKey) {
    tenantIdKey.wrapString(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    return processColumnFamily.get(tenantAwareProcessDefinitionKey);
  }

  @Override
  public @Nullable PersistedProcess findProcessByProcessIdAndVersion(
      final String tenantId, final DirectBuffer bpmnProcessId, final long version) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    processVersion.wrapLong(version);

    final PersistedProcess processWithVersionAndId =
        processByIdAndVersionColumnFamily.get(tenantAwareProcessIdAndVersionKey);
    if (processWithVersionAndId == null) {
      return null;
    }
    // the by-version copy is written once at deployment and goes stale when the state changes
    return findProcessByKey(tenantId, processWithVersionAndId.getKey());
  }

  @Override
  public @Nullable Long findProcessDefinitionKeyByDeploymentKey(
      final String tenantId, final DirectBuffer bpmnProcessId, final long deploymentKey) {
    tenantIdKey.wrapString(tenantId);
    processId.wrapBuffer(bpmnProcessId);
    this.deploymentKey.wrapLong(deploymentKey);

    final var foreignKey =
        processDefinitionKeyByProcessIdAndDeploymentKeyColumnFamily.get(
            tenantAwareProcessIdAndDeploymentKey);
    return foreignKey != null ? foreignKey.inner().wrappedKey().getValue() : null;
  }
}
