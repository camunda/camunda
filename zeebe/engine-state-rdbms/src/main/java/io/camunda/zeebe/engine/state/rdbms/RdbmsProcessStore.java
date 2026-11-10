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
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.PartitionStateRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.PendingProcessDeletionRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessDefinitionRow;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessIdKey;
import io.camunda.zeebe.engine.state.rdbms.EngineStateMapper.ProcessVersionRow;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
 *   <li>The lookups by deployment key and by version tag are columns of the process, and the digest
 *       a column of the version info. A lookup is moved from the process that has it when it is set
 *       for another one, and the version tag lookup is remembered when its process is deleted, see
 *       {@link DeletedVersionTagLookups}.
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

  /** The key of the partition state that {@link #markProcessDefinitionRestoreAsRun()} sets. */
  static final String PROCESS_DEFINITION_RESTORE_KEY =
      DbProcessStore.PROCESS_DEFINITION_RESTORE_KEY_VALUE;

  private static final String MARKER_VALUE = "true";

  /** How many processes {@link #forEachProcess} loads at once. */
  private static final int PROCESS_PAGE_SIZE = 100;

  private static final int DEFAULT_VERSION_VALUE = 0;

  private static final Comparator<ProcessDefinitionRow> BY_KEY =
      Comparator.comparingLong(ProcessDefinitionRow::processDefinitionKey);

  private final EngineStateRdbms rdbms;
  private final int partitionId;

  private final EntityCache<Long, ProcessDefinitionRow> processes;
  private final EntityCache<ProcessIdKey, ProcessVersionRow> versions;
  private final EntityCache<PendingProcessDeletionRow, PendingProcessDeletionRow> pendingDeletions;
  private final EntityCache<String, PartitionStateRow> partitionState;

  private final VersionInfoCache versionInfos = new VersionInfoCache();
  private final DeletedVersionTagLookups deletedVersionTagLookups = new DeletedVersionTagLookups();

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
    pendingDeletions =
        rdbms.cache(
            "pending process deletion",
            Function.identity(),
            EngineStateMapper::selectPendingProcessDeletion,
            EngineStateMapper::insertPendingProcessDeletion,
            EngineStateMapper::updatePendingProcessDeletion,
            EngineStateMapper::deletePendingProcessDeletion);
    partitionState =
        rdbms.cache(
            "partition state",
            PartitionStateRow::key,
            EngineStateMapper::selectPartitionState,
            EngineStateMapper::insertPartitionState,
            EngineStateMapper::updatePartitionState,
            EngineStateMapper::deletePartitionState);

    transactionContext.addTransactionListener(versionInfos);
    transactionContext.addTransactionListener(deletedVersionTagLookups);
  }

  @Override
  public void putProcess(final long processDefinitionKey, final ProcessRecord processRecord) {
    final var process = new PersistedProcess();
    process.wrap(processRecord, processDefinitionKey);
    upsertKeepingLookups(toRow(process));
  }

  @Override
  public void updateProcess(final String tenantId, final PersistedProcess process) {
    final var row = toRow(process);
    final var existing = processes.get(row.processDefinitionKey());
    processes.update(
        existing == null
            ? row
            : row.withLookups(existing.deploymentKeyLookup(), existing.versionTagLookup()));
  }

  @Override
  public void updateProcessCopyByVersion(final String tenantId, final PersistedProcess process) {
    // a process is one row, there is no copy
  }

  @Override
  public void deleteProcess(final ProcessRecord processRecord) {
    final var tenantId = processRecord.getTenantId();
    final long processDefinitionKey = processRecord.getProcessDefinitionKey();
    final var row = findRow(tenantId, processDefinitionKey);
    if (row == null) {
      throw new ZeebeDbInconsistentException(
          "Expected to delete process %d of tenant '%s', but it does not exist"
              .formatted(processDefinitionKey, tenantId));
    }
    processes.delete(processDefinitionKey);
    // older processes have no deployment key, so there may be no lookup to delete
    clearDeploymentLookup(
        tenantId, processRecord.getBpmnProcessId(), processRecord.getDeploymentKey());
    if (row.versionTagLookup() != null) {
      // the row's lookup goes with it, but the version tag lookup of the RocksDB state survives
      // the deletion of its process until the caller has looked at it
      deletedVersionTagLookups.add(
          tenantId, row.bpmnProcessId(), row.versionTagLookup(), processDefinitionKey);
    }
  }

  @Override
  public void putDigest(
      final String tenantId, final DirectBuffer bpmnProcessId, final DirectBuffer digest) {
    final var key = new ProcessIdKey(tenantId, bufferAsString(bpmnProcessId));
    final var row = versions.get(key);
    if (row == null) {
      throw new ZeebeDbInconsistentException(
          "Expected to put the digest of process '%s' of tenant '%s', but no version is stored"
              .formatted(key.bpmnProcessId(), tenantId));
    }
    versions.update(row.withDigest(bufferAsArray(digest)));
  }

  @Override
  public @Nullable DirectBuffer getDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    final var row = versions.get(new ProcessIdKey(tenantId, bufferAsString(bpmnProcessId)));
    return row == null || row.digest() == null ? null : new UnsafeBuffer(row.digest().clone());
  }

  @Override
  public void deleteDigest(final String tenantId, final DirectBuffer bpmnProcessId) {
    final var row = versions.get(new ProcessIdKey(tenantId, bufferAsString(bpmnProcessId)));
    if (row != null && row.digest() != null) {
      versions.update(row.withDigest(null));
    }
  }

  @Override
  public void putProcessKeyByDeploymentKey(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final long deploymentKey,
      final long processDefinitionKey) {
    requireProcessForLookup(processDefinitionKey, "deployment key");
    // moves the lookup: another process may have it, and this one may already have it
    clearDeploymentLookup(tenantId, bufferAsString(bpmnProcessId), deploymentKey);
    final var process = Objects.requireNonNull(processes.get(processDefinitionKey));
    processes.update(process.withLookups(deploymentKey, process.versionTagLookup()));
  }

  @Override
  public void putProcessKeyByVersionTag(
      final String tenantId,
      final DirectBuffer bpmnProcessId,
      final String versionTag,
      final long processDefinitionKey) {
    requireProcessForLookup(processDefinitionKey, "version tag");
    final var processId = bufferAsString(bpmnProcessId);
    // moves the lookup: another process may have it, and this one may already have it
    clearVersionTagLookup(tenantId, processId, versionTag);
    final var process = Objects.requireNonNull(processes.get(processDefinitionKey));
    processes.update(process.withLookups(process.deploymentKeyLookup(), versionTag));
    deletedVersionTagLookups.remove(tenantId, processId, versionTag);
  }

  @Override
  public long findProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    final var bpmnProcessIdString = bufferAsString(bpmnProcessId);
    final var rows = rowsWithVersionTagLookup(tenantId, bpmnProcessIdString, versionTag);
    if (!rows.isEmpty()) {
      return rows.getFirst().processDefinitionKey();
    }
    final var deletedKey = deletedVersionTagLookups.get(tenantId, bpmnProcessIdString, versionTag);
    return deletedKey == null ? NO_PROCESS : deletedKey;
  }

  @Override
  public void deleteProcessKeyByVersionTag(
      final String tenantId, final DirectBuffer bpmnProcessId, final String versionTag) {
    final var bpmnProcessIdString = bufferAsString(bpmnProcessId);
    clearVersionTagLookup(tenantId, bpmnProcessIdString, versionTag);
    deletedVersionTagLookups.remove(tenantId, bpmnProcessIdString, versionTag);
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
    upsert(
        partitionState,
        PROCESS_DEFINITION_RESTORE_KEY,
        new PartitionStateRow(PROCESS_DEFINITION_RESTORE_KEY, MARKER_VALUE));
  }

  @Override
  public boolean hasRunProcessDefinitionRestore() {
    return partitionState.get(PROCESS_DEFINITION_RESTORE_KEY) != null;
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
    final var rows =
        rowsWithDeploymentLookup(tenantId, bufferAsString(bpmnProcessId), deploymentKey);
    return rows.isEmpty() ? null : rows.getFirst().processDefinitionKey();
  }

  /** The process that a lookup is about to point at. */
  private void requireProcessForLookup(final long processDefinitionKey, final String lookup) {
    if (processes.get(processDefinitionKey) == null) {
      throw new ZeebeDbInconsistentException(
          "Expected to look up process %d by %s, but it does not exist"
              .formatted(processDefinitionKey, lookup));
    }
  }

  /** Removes the lookup by deployment key, from the process that has it. */
  private void clearDeploymentLookup(
      final String tenantId, final String bpmnProcessId, final long deploymentKey) {
    for (final var row : rowsWithDeploymentLookup(tenantId, bpmnProcessId, deploymentKey)) {
      processes.update(row.withLookups(null, row.versionTagLookup()));
    }
  }

  /** Removes the lookup by version tag, from the process that has it. */
  private void clearVersionTagLookup(
      final String tenantId, final String bpmnProcessId, final String versionTag) {
    for (final var row : rowsWithVersionTagLookup(tenantId, bpmnProcessId, versionTag)) {
      processes.update(row.withLookups(row.deploymentKeyLookup(), null));
    }
  }

  private List<ProcessDefinitionRow> rowsWithDeploymentLookup(
      final String tenantId, final String bpmnProcessId, final long deploymentKey) {
    return queryProcesses(
        mapper ->
            mapper.selectProcessDefinitionsByDeploymentLookup(
                partitionId, tenantId, bpmnProcessId, deploymentKey),
        row ->
            row.tenantId().equals(tenantId)
                && row.bpmnProcessId().equals(bpmnProcessId)
                && Long.valueOf(deploymentKey).equals(row.deploymentKeyLookup()));
  }

  private List<ProcessDefinitionRow> rowsWithVersionTagLookup(
      final String tenantId, final String bpmnProcessId, final String versionTag) {
    return queryProcesses(
        mapper ->
            mapper.selectProcessDefinitionsByVersionTagLookup(
                partitionId, tenantId, bpmnProcessId, versionTag),
        row ->
            row.tenantId().equals(tenantId)
                && row.bpmnProcessId().equals(bpmnProcessId)
                && versionTag.equals(row.versionTagLookup()));
  }

  /** The committed processes of the query, with the staged writes applied. */
  private List<ProcessDefinitionRow> queryProcesses(
      final EngineStateMyBatis.MapperCall<List<ProcessDefinitionRow>> query,
      final Predicate<ProcessDefinitionRow> stagedMatches) {
    return processes.query(rdbms.read(query), stagedMatches, BY_KEY);
  }

  /** Inserts the process, or updates it if it exists, in which case it keeps its lookups. */
  private void upsertKeepingLookups(final ProcessDefinitionRow row) {
    final var existing = processes.get(row.processDefinitionKey());
    if (existing == null) {
      processes.insert(row);
    } else {
      processes.update(
          row.withLookups(existing.deploymentKeyLookup(), existing.versionTagLookup()));
    }
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
        null,
        null,
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
      final var existing = versions.get(key);
      if (existing == null) {
        versions.insert(toRow(key, versionInfo, null));
      } else {
        versions.update(toRow(key, versionInfo, existing.digest()));
      }
    }

    void removeVersion(final ProcessIdKey key, final long version) {
      final var existing = versions.get(key);
      if (existing == null) {
        throw new ZeebeDbInconsistentException(
            "Expected to remove version %d of process '%s' of tenant '%s', but no version is stored"
                .formatted(version, key.bpmnProcessId(), key.tenantId()));
      }
      final var versionInfo = get(key);
      changedInTransaction.add(key);
      versionInfo.removeKnownVersion(version);
      versions.update(toRow(key, versionInfo, existing.digest()));
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

    private static ProcessVersionRow toRow(
        final ProcessIdKey key, final VersionInfo versionInfo, final byte @Nullable [] digest) {
      return new ProcessVersionRow(
          key.tenantId(),
          key.bpmnProcessId(),
          versionInfo.getHighestVersion(),
          versionInfo.getLatestVersion(),
          digest,
          serialize(versionInfo));
    }
  }

  /**
   * The version tag lookups of deleted processes, by the tenant, process id and version tag they
   * had, pointing at the deleted process.
   *
   * <p>The RocksDB state keeps a lookup when its process is deleted, and the event appliers that
   * delete a process ask for the lookup afterwards to find out whether to move or remove it. Here a
   * lookup is a column of its process, so it is remembered until the lookup is set or deleted. The
   * appliers do that in the transaction of the deletion, so nothing is kept for long, and it is not
   * persisted. A rollback forgets what its transaction added, as the process is back then.
   */
  private static final class DeletedVersionTagLookups implements TransactionListener {

    private record Key(String tenantId, String bpmnProcessId, String versionTag) {}

    private final Map<Key, Long> deleted = new HashMap<>();
    private final Set<Key> addedInTransaction = new HashSet<>();

    void add(
        final String tenantId,
        final String bpmnProcessId,
        final String versionTag,
        final long processDefinitionKey) {
      final var key = new Key(tenantId, bpmnProcessId, versionTag);
      deleted.put(key, processDefinitionKey);
      addedInTransaction.add(key);
    }

    @Nullable Long get(final String tenantId, final String bpmnProcessId, final String versionTag) {
      return deleted.get(new Key(tenantId, bpmnProcessId, versionTag));
    }

    void remove(final String tenantId, final String bpmnProcessId, final String versionTag) {
      deleted.remove(new Key(tenantId, bpmnProcessId, versionTag));
    }

    @Override
    public void onCommit() {
      addedInTransaction.clear();
    }

    @Override
    public void onRollback() {
      addedInTransaction.forEach(deleted::remove);
      addedInTransaction.clear();
    }
  }
}
