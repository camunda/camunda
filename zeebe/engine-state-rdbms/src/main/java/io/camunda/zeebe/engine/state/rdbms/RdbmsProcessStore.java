/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.engine.state.rdbms.Payloads.serialize;
import static io.camunda.zeebe.engine.state.rdbms.Payloads.wrapCopy;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.jdbc.EntityCache;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.deployment.DbProcessStore;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess;
import io.camunda.zeebe.engine.state.deployment.ProcessDefinitionCache;
import io.camunda.zeebe.engine.state.deployment.ProcessStore;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.engine.state.deployment.VersionManager.ResourceVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.PersistedProcessVisitor;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.PendingProcessDeletionRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessByDeploymentKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessByDeploymentRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessByVersionTagKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessByVersionTagRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessDefinitionRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessDigestRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessIdKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessVersionRow;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the process state in a relational database instead of RocksDB.
 *
 * <p>Like {@link RdbmsElementInstanceStore}, each table has an {@link EntityCache} that stages the
 * writes until the {@link JdbcTransactionContext} commits, reads see the staged writes first, and
 * every operation joins the current transaction or runs in its own. All SQL is in the {@link
 * EngineStateMapper}.
 *
 * <p>Differences to {@link DbProcessStore}, none of them observable through {@link
 * MutableProcessState}:
 *
 * <ul>
 *   <li>A process is one row, found by key or by id and version. RocksDB keeps a second copy by id
 *       and version that isn't updated when the state of the process changes.
 *   <li>Processes are iterated in key order across tenants, not grouped by tenant.
 *   <li>Writes that RocksDB only does on existing or missing keys check this up front and throw
 *       {@link ZeebeDbInconsistentException}, like RocksDB with consistency checks enabled.
 *   <li>{@link #forEachVersion} stops when the visitor returns false.
 * </ul>
 *
 * <p>The version infos of the process ids are kept in memory, as they are read for every process
 * instance created by process id. Like the {@link ProcessDefinitionCache} that loads its misses
 * from this store, they listen to the JDBC transaction context: they reflect the rows in the
 * database, so they follow the outcome of the JDBC transaction. In mixed mode, a RocksDB commit
 * that fails after the JDBC commit leaves the rows committed, and the caches keep matching them.
 *
 * <p>Reads return new objects.
 */
public final class RdbmsProcessStore implements ProcessStore {

  /** The flag that {@link #markProcessDefinitionRestoreAsRun()} sets. */
  static final String PROCESS_DEFINITION_RESTORE_FLAG =
      DbProcessStore.PROCESS_DEFINITION_RESTORE_KEY_VALUE;

  /** How many processes {@link #forEachProcess} loads at once. */
  private static final int PROCESS_PAGE_SIZE = 100;

  private static final int DEFAULT_VERSION_VALUE = 0;

  private static final Comparator<ProcessDefinitionRow> BY_KEY =
      Comparator.comparingLong(ProcessDefinitionRow::processDefinitionKey);

  private final EngineStateRdbms rdbms;
  private final int partitionId;

  private final EntityCache<Long, ProcessDefinitionRow> processes;
  private final EntityCache<ProcessIdKey, ProcessVersionRow> versions;
  private final EntityCache<ProcessIdKey, ProcessDigestRow> digests;
  private final EntityCache<ProcessByDeploymentKey, ProcessByDeploymentRow> processesByDeployment;
  private final EntityCache<ProcessByVersionTagKey, ProcessByVersionTagRow> processesByVersionTag;
  private final EntityCache<PendingProcessDeletionRow, PendingProcessDeletionRow> pendingDeletions;
  private final EntityCache<String, String> flags;

  private final VersionInfoCache versionInfos = new VersionInfoCache();

  public RdbmsProcessStore(final JdbcTransactionContext transactionContext, final int partitionId) {
    rdbms = new EngineStateRdbms(transactionContext, partitionId);
    this.partitionId = partitionId;

    processes =
        rdbms.cache(
            "process",
            ProcessDefinitionRow::processDefinitionKey,
            EngineStateMapper::selectProcessDefinition,
            EngineStateMapper::insertProcessDefinition,
            EngineStateMapper::updateProcessDefinition,
            EngineStateMapper::deleteProcessDefinition);
    versions =
        rdbms.cache(
            "process version info",
            ProcessVersionRow::key,
            EngineStateMapper::selectProcessVersion,
            EngineStateMapper::insertProcessVersion,
            EngineStateMapper::updateProcessVersion,
            EngineStateMapper::deleteProcessVersion);
    digests =
        rdbms.cache(
            "process digest",
            ProcessDigestRow::key,
            EngineStateMapper::selectProcessDigest,
            EngineStateMapper::insertProcessDigest,
            EngineStateMapper::updateProcessDigest,
            EngineStateMapper::deleteProcessDigest);
    processesByDeployment =
        rdbms.cache(
            "process by deployment",
            ProcessByDeploymentRow::key,
            EngineStateMapper::selectProcessByDeployment,
            EngineStateMapper::insertProcessByDeployment,
            EngineStateMapper::updateProcessByDeployment,
            EngineStateMapper::deleteProcessByDeployment);
    processesByVersionTag =
        rdbms.cache(
            "process by version tag",
            ProcessByVersionTagRow::key,
            EngineStateMapper::selectProcessByVersionTag,
            EngineStateMapper::insertProcessByVersionTag,
            EngineStateMapper::updateProcessByVersionTag,
            EngineStateMapper::deleteProcessByVersionTag);
    pendingDeletions =
        rdbms.cache(
            "pending process deletion",
            Function.identity(),
            EngineStateMapper::selectPendingProcessDeletion,
            EngineStateMapper::insertPendingProcessDeletion,
            EngineStateMapper::updatePendingProcessDeletion,
            EngineStateMapper::deletePendingProcessDeletion);
    flags =
        rdbms.cache(
            "partition flag",
            Function.identity(),
            EngineStateMapper::selectPartitionFlag,
            EngineStateMapper::insertPartitionFlag,
            EngineStateMapper::updatePartitionFlag,
            EngineStateMapper::deletePartitionFlag);

    transactionContext.addTransactionListener(versionInfos);
  }

  @Override
  public void putProcess(final long processDefinitionKey, final ProcessRecord processRecord) {
    final var process = new PersistedProcess();
    process.wrap(processRecord, processDefinitionKey);
    upsert(processes, processDefinitionKey, toRow(process));
  }

  @Override
  public void updateProcess(final String tenantId, final PersistedProcess process) {
    processes.update(toRow(process));
  }

  @Override
  public void updateProcessCopyByVersion(final String tenantId, final PersistedProcess process) {
    // a process is one row, there is no copy
  }

  @Override
  public void deleteProcess(final ProcessRecord processRecord) {
    final var tenantId = processRecord.getTenantId();
    final long processDefinitionKey = processRecord.getProcessDefinitionKey();
    if (findRow(tenantId, processDefinitionKey) == null) {
      throw new ZeebeDbInconsistentException(
          "Expected to delete process %d of tenant '%s', but it does not exist"
              .formatted(processDefinitionKey, tenantId));
    }
    processes.delete(processDefinitionKey);
    // older processes have no deployment key, and deleting a missing row is no error
    processesByDeployment.delete(
        new ProcessByDeploymentKey(
            tenantId, processRecord.getBpmnProcessId(), processRecord.getDeploymentKey()));
  }

  @Override
  public void putDigest(
      final String tenantId, final DirectBuffer bpmnProcessId, final DirectBuffer digest) {
    final var row =
        new ProcessDigestRow(tenantId, bufferAsString(bpmnProcessId), bufferAsArray(digest));
    upsert(digests, row.key(), row);
  }

  @Override
  public @Nullable DirectBuffer getDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    final var row = digests.get(new ProcessIdKey(tenantId, bufferAsString(bpmnProcessId)));
    return row == null ? null : new UnsafeBuffer(row.digest().clone());
  }

  @Override
  public void deleteDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    digests.delete(new ProcessIdKey(tenantId, bufferAsString(bpmnProcessId)));
  }

  @Override
  public void putProcessKeyByDeploymentKey(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final long deploymentKey,
      final long processDefinitionKey) {
    final var row =
        new ProcessByDeploymentRow(
            tenantId, bufferAsString(bpmnProcessId), deploymentKey, processDefinitionKey);
    upsert(processesByDeployment, row.key(), row);
  }

  @Override
  public void putProcessKeyByVersionTag(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final String versionTag,
      final long processDefinitionKey) {
    final var row =
        new ProcessByVersionTagRow(
            tenantId, bufferAsString(bpmnProcessId), versionTag, processDefinitionKey);
    upsert(processesByVersionTag, row.key(), row);
  }

  @Override
  public long findProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    final var row =
        processesByVersionTag.get(
            new ProcessByVersionTagKey(tenantId, bufferAsString(bpmnProcessId), versionTag));
    return row == null ? NO_PROCESS : row.processDefinitionKey();
  }

  @Override
  public void deleteProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    processesByVersionTag.delete(
        new ProcessByVersionTagKey(tenantId, bufferAsString(bpmnProcessId), versionTag));
  }

  @Override
  public void addVersion(final String bpmnProcessId, final long version, final String tenantId) {
    versionInfos.addVersion(new ProcessIdKey(tenantId, bpmnProcessId), version);
  }

  @Override
  public void removeVersion(final String bpmnProcessId, final long version, final String tenantId) {
    versionInfos.removeVersion(new ProcessIdKey(tenantId, bpmnProcessId), version);
  }

  @Override
  public long getLatestVersion(final DirectBuffer bpmnProcessId, final String tenantId) {
    return getLatestVersion(bufferAsString(bpmnProcessId), tenantId);
  }

  @Override
  public long getLatestVersion(final String bpmnProcessId, final String tenantId) {
    return versionInfos.get(new ProcessIdKey(tenantId, bpmnProcessId)).getLatestVersion();
  }

  @Override
  public long getHighestVersion(final String bpmnProcessId, final String tenantId) {
    return versionInfos.get(new ProcessIdKey(tenantId, bpmnProcessId)).getHighestVersion();
  }

  @Override
  public Optional<Integer> findVersionBefore(
      final String bpmnProcessId, final long version, final String tenantId) {
    return versionInfos.get(new ProcessIdKey(tenantId, bpmnProcessId)).findVersionBefore(version);
  }

  @Override
  public List<Long> getKnownVersions(final String bpmnProcessId, final String tenantId) {
    return versionInfos.get(new ProcessIdKey(tenantId, bpmnProcessId)).getKnownVersions();
  }

  @Override
  public void forEachVersion(final ResourceVisitor visitor) {
    final var committed = rdbms.read(mapper -> mapper.selectProcessVersions(partitionId));
    final var rows =
        versions.query(
            committed,
            row -> true,
            Comparator.comparing(ProcessVersionRow::tenantId)
                .thenComparing(ProcessVersionRow::bpmnProcessId));
    for (final var row : rows) {
      final var versionInfo = wrapCopy(new VersionInfo(), row.payload());
      if (!visitor.visit(wrapString(row.bpmnProcessId()), row.tenantId(), versionInfo)) {
        return;
      }
    }
  }

  @Override
  public void clearVersions() {
    versionInfos.clear();
  }

  @Override
  public void addPendingDeletion(final long processDefinitionKey, final int partitionId) {
    final var row = new PendingProcessDeletionRow(processDefinitionKey, partitionId);
    if (pendingDeletions.get(row) != null) {
      throw new ZeebeDbInconsistentException(
          "Expected to add a pending deletion of process %d for partition %d, but it already exists"
              .formatted(processDefinitionKey, partitionId));
    }
    pendingDeletions.insert(row);
  }

  @Override
  public void removePendingDeletion(final long processDefinitionKey, final int partitionId) {
    final var row = new PendingProcessDeletionRow(processDefinitionKey, partitionId);
    if (pendingDeletions.get(row) == null) {
      throw new ZeebeDbInconsistentException(
          "Expected to remove the pending deletion of process %d for partition %d, but it does not exist"
              .formatted(processDefinitionKey, partitionId));
    }
    pendingDeletions.delete(row);
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey, final int partitionId) {
    return pendingDeletions.get(new PendingProcessDeletionRow(processDefinitionKey, partitionId))
        != null;
  }

  @Override
  public boolean hasPendingDeletion(final long processDefinitionKey) {
    return !readPendingDeletions(processDefinitionKey).isEmpty();
  }

  @Override
  public Set<Integer> getPendingDeletionPartitions(final long processDefinitionKey) {
    final Set<Integer> partitions = new LinkedHashSet<>();
    for (final var row : readPendingDeletions(processDefinitionKey)) {
      partitions.add(row.pendingPartitionId());
    }
    return partitions;
  }

  @Override
  public void markProcessDefinitionRestoreAsRun() {
    upsert(flags, PROCESS_DEFINITION_RESTORE_FLAG, PROCESS_DEFINITION_RESTORE_FLAG);
  }

  @Override
  public boolean hasRunProcessDefinitionRestore() {
    return flags.get(PROCESS_DEFINITION_RESTORE_FLAG) != null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Visits the processes in key order, a page at a time, so the visitor may change processes
   * while iterating.
   */
  @Override
  public void forEachProcess(
      final @Nullable ProcessIdentifier previousProcess, final PersistedProcessVisitor visitor) {
    long afterKey =
        previousProcess == null ? Long.MIN_VALUE : previousProcess.processDefinitionKey();
    while (true) {
      final long pageStart = afterKey;
      final var committed =
          rdbms.read(
              mapper ->
                  mapper.selectProcessDefinitionsAfter(partitionId, pageStart, PROCESS_PAGE_SIZE));
      final boolean lastPage = committed.size() < PROCESS_PAGE_SIZE;
      // staged processes beyond the page's last committed key belong to a later page
      final long pageEnd = lastPage ? Long.MAX_VALUE : committed.getLast().processDefinitionKey();
      final var page =
          processes.query(
              committed,
              row ->
                  row.processDefinitionKey() > pageStart && row.processDefinitionKey() <= pageEnd,
              BY_KEY);
      for (final var row : page) {
        if (!visitor.visit(toProcess(row))) {
          return;
        }
      }
      if (lastPage) {
        return;
      }
      afterKey = pageEnd;
    }
  }

  @Override
  public @Nullable PersistedProcess findProcessByKey(
      final String tenantId, final long processDefinitionKey) {
    final var row = findRow(tenantId, processDefinitionKey);
    return row == null ? null : toProcess(row);
  }

  @Override
  public @Nullable PersistedProcess findProcessByProcessIdAndVersion(
      final String tenantId, final DirectBuffer bpmnProcessId, final long version) {
    final var row = findRowByVersion(tenantId, bufferAsString(bpmnProcessId), (int) version);
    return row == null ? null : toProcess(row);
  }

  @Override
  public @Nullable Long findProcessDefinitionKeyByDeploymentKey(
      final String tenantId, final DirectBuffer bpmnProcessId, final long deploymentKey) {
    final var row =
        processesByDeployment.get(
            new ProcessByDeploymentKey(tenantId, bufferAsString(bpmnProcessId), deploymentKey));
    return row == null ? null : row.processDefinitionKey();
  }

  /** Inserts the entity, or updates it if a row with its key exists. */
  private static <K, E> void upsert(final EntityCache<K, E> cache, final K key, final E entity) {
    if (cache.get(key) == null) {
      cache.insert(entity);
    } else {
      cache.update(entity);
    }
  }

  /** The row of the process, if it exists and belongs to the tenant. */
  private @Nullable ProcessDefinitionRow findRow(
      final String tenantId, final long processDefinitionKey) {
    final var row = processes.get(processDefinitionKey);
    return row != null && row.tenantId().equals(tenantId) ? row : null;
  }

  /**
   * The process with the given id and version. If the version was stored under several keys, the
   * one stored last, i.e. with the highest key, like the RocksDB state's by-version copy.
   */
  private @Nullable ProcessDefinitionRow findRowByVersion(
      final String tenantId, final String bpmnProcessId, final int version) {
    final var committed =
        rdbms.read(
            mapper ->
                mapper.selectProcessDefinitionsByVersion(
                    partitionId, tenantId, bpmnProcessId, version));
    final var rows =
        processes.query(
            committed,
            row ->
                row.tenantId().equals(tenantId)
                    && row.bpmnProcessId().equals(bpmnProcessId)
                    && row.version() == version,
            BY_KEY);
    return rows.isEmpty() ? null : rows.getLast();
  }

  /** The pending deletions of the process definition, ordered by partition. */
  private List<PendingProcessDeletionRow> readPendingDeletions(final long processDefinitionKey) {
    final var committed =
        rdbms.read(
            mapper -> mapper.selectPendingProcessDeletions(partitionId, processDefinitionKey));
    return pendingDeletions.query(
        committed,
        row -> row.processDefinitionKey() == processDefinitionKey,
        Comparator.comparingInt(PendingProcessDeletionRow::pendingPartitionId));
  }

  private static ProcessDefinitionRow toRow(final PersistedProcess process) {
    final var versionTag = process.getVersionTag();
    return new ProcessDefinitionRow(
        process.getKey(),
        process.getTenantId(),
        bufferAsString(process.getBpmnProcessId()),
        process.getVersion(),
        versionTag.isEmpty() ? null : versionTag,
        process.getDeploymentKey(),
        bufferAsString(process.getResourceName()),
        (short) process.getState().ordinal(),
        serialize(process));
  }

  private static PersistedProcess toProcess(final ProcessDefinitionRow row) {
    return wrapCopy(new PersistedProcess(), row.payload());
  }

  /**
   * The version infos of the process ids, kept in memory once read. Changing a version info stages
   * its row and changes the cached one; a rollback evicts the version infos changed in the
   * transaction, so the next read loads the committed row again.
   */
  private final class VersionInfoCache implements TransactionListener {

    private final Map<ProcessIdKey, VersionInfo> cached = new HashMap<>();
    private final Set<ProcessIdKey> changedInTransaction = new HashSet<>();

    VersionInfo get(final ProcessIdKey key) {
      var versionInfo = cached.get(key);
      if (versionInfo == null) {
        final var row = versions.get(key);
        versionInfo =
            row == null
                ? new VersionInfo().setHighestVersionIfHigher(DEFAULT_VERSION_VALUE)
                : wrapCopy(new VersionInfo(), row.payload());
        cached.put(key, versionInfo);
      }
      return versionInfo;
    }

    void addVersion(final ProcessIdKey key, final long version) {
      final var versionInfo = get(key);
      changedInTransaction.add(key);
      versionInfo.addKnownVersion(version);
      upsert(versions, key, toRow(key, versionInfo));
    }

    void removeVersion(final ProcessIdKey key, final long version) {
      if (versions.get(key) == null) {
        throw new ZeebeDbInconsistentException(
            "Expected to remove version %d of process '%s' of tenant '%s', but no version is stored"
                .formatted(version, key.bpmnProcessId(), key.tenantId()));
      }
      final var versionInfo = get(key);
      changedInTransaction.add(key);
      versionInfo.removeKnownVersion(version);
      versions.update(toRow(key, versionInfo));
    }

    void clear() {
      cached.clear();
    }

    @Override
    public void onCommit() {
      changedInTransaction.clear();
    }

    @Override
    public void onRollback() {
      changedInTransaction.forEach(cached::remove);
      changedInTransaction.clear();
    }

    private static ProcessVersionRow toRow(final ProcessIdKey key, final VersionInfo versionInfo) {
      return new ProcessVersionRow(
          key.tenantId(),
          key.bpmnProcessId(),
          versionInfo.getHighestVersion(),
          versionInfo.getLatestVersion(),
          serialize(versionInfo));
    }
  }
}
