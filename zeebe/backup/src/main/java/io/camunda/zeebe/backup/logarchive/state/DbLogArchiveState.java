/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.ArchivedSnapshotValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.CoveredCheckpointValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.IndexChunkValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Access to the replicated log archive state (spec section 5), stored in the {@code LOG_ARCHIVE_*}
 * column families.
 *
 * <p>The read methods return copies and can be used by anyone holding an instance, e.g. the
 * processor, the leader components or the compaction cap. The mutation methods must only be called
 * by the event appliers, so that replaying events yields the same state as processing commands.
 */
public final class DbLogArchiveState {

  private static final String META_KEY = "meta";

  private final DbString metaKey = new DbString();
  private final LogArchiveMeta metaValue = new LogArchiveMeta();
  private final ColumnFamily<DbString, LogArchiveMeta> metaColumnFamily;

  private final DbLong openObjectKey = new DbLong();
  private final OpenObjectDbValue openObjectValue = new OpenObjectDbValue();
  private final ColumnFamily<DbLong, OpenObjectDbValue> openObjectsColumnFamily;

  private final DbLong chunkKey = new DbLong();
  private final ObjectDbValue<IndexChunk> chunkValue = new ObjectDbValue<>(IndexChunk::new);
  private final ColumnFamily<DbLong, ObjectDbValue<IndexChunk>> chunksColumnFamily;

  private final DbLong snapshotKey = new DbLong();
  private final ObjectDbValue<ArchivedSnapshot> snapshotValue =
      new ObjectDbValue<>(ArchivedSnapshot::new);
  private final ColumnFamily<DbLong, ObjectDbValue<ArchivedSnapshot>> snapshotsColumnFamily;

  private final DbLong outboxKey = new DbLong();
  private final OutboxDbValue outboxValue = new OutboxDbValue();
  private final ColumnFamily<DbLong, OutboxDbValue> outboxColumnFamily;

  private final DbLong pendingCheckpointKey = new DbLong();
  private final ObjectDbValue<CoveredCheckpoint> pendingCheckpointValue =
      new ObjectDbValue<>(CoveredCheckpoint::new);
  private final ColumnFamily<DbLong, ObjectDbValue<CoveredCheckpoint>>
      pendingCheckpointsColumnFamily;

  private final DbLong backupCheckpointKey = new DbLong();
  private final ObjectDbValue<CoveredCheckpoint> backupCheckpointValue =
      new ObjectDbValue<>(CoveredCheckpoint::new);
  private final ColumnFamily<DbLong, ObjectDbValue<CoveredCheckpoint>>
      backupCheckpointsColumnFamily;

  private final DbLong unmergedObjectKey = new DbLong();
  private final UnmergedObjectDbValue unmergedObjectValue = new UnmergedObjectDbValue();
  private final ColumnFamily<DbLong, UnmergedObjectDbValue> unmergedObjectsColumnFamily;

  private final DbLong mergedObjectKey = new DbLong();
  private final ObjectDbValue<LogObject> mergedObjectValue = new ObjectDbValue<>(LogObject::new);
  private final ColumnFamily<DbLong, ObjectDbValue<LogObject>> mergedObjectsColumnFamily;

  public DbLogArchiveState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    metaKey.wrapString(META_KEY);
    metaColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_META, transactionContext, metaKey, metaValue);
    openObjectsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_OPEN_OBJECTS,
            transactionContext,
            openObjectKey,
            openObjectValue);
    chunksColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_CHUNKS, transactionContext, chunkKey, chunkValue);
    snapshotsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_SNAPSHOTS, transactionContext, snapshotKey, snapshotValue);
    outboxColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_OUTBOX, transactionContext, outboxKey, outboxValue);
    pendingCheckpointsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_PENDING_CHECKPOINTS,
            transactionContext,
            pendingCheckpointKey,
            pendingCheckpointValue);
    backupCheckpointsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_BACKUP_CHECKPOINTS,
            transactionContext,
            backupCheckpointKey,
            backupCheckpointValue);
    unmergedObjectsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_UNMERGED_OBJECTS,
            transactionContext,
            unmergedObjectKey,
            unmergedObjectValue);
    mergedObjectsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.LOG_ARCHIVE_MERGED_OBJECTS,
            transactionContext,
            mergedObjectKey,
            mergedObjectValue);
  }

  // ---------------------------------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------------------------------

  /** Returns a copy of the meta row, or the initial meta if no timeline was ever started. */
  public LogArchiveMeta meta() {
    metaKey.wrapString(META_KEY);
    final var stored = metaColumnFamily.get(metaKey);
    return stored == null ? new LogArchiveMeta() : stored.copy();
  }

  /** All open objects in index order. */
  public List<OpenObject> openObjects() {
    return firstOpenObjects(Integer.MAX_VALUE);
  }

  /** At most the first {@code count} open objects in index order. */
  public List<OpenObject> firstOpenObjects(final int count) {
    final var result = new ArrayList<OpenObject>();
    if (count <= 0) {
      return result;
    }
    openObjectsColumnFamily.whileTrue(
        (key, value) -> {
          result.add(OpenObject.copyOf(value));
          return result.size() < count;
        });
    return result;
  }

  public long openObjectCount() {
    // not count(): it opens a transaction without closing it when called outside of one
    final var count = new AtomicLong();
    openObjectsColumnFamily.forEachKey(key -> count.incrementAndGet());
    return count.get();
  }

  /** All sealed chunks in chunk number order. */
  public List<IndexChunk> chunks() {
    final var result = new ArrayList<IndexChunk>();
    chunksColumnFamily.forEach(value -> result.add(new IndexChunk().copy(value.value())));
    return result;
  }

  /** All archived snapshots in archive seq order. */
  public List<ArchivedSnapshot> snapshots() {
    final var result = new ArrayList<ArchivedSnapshot>();
    snapshotsColumnFamily.forEach(value -> result.add(new ArchivedSnapshot().copy(value.value())));
    return result;
  }

  public boolean containsSnapshot(final String snapshotId) {
    final var found = new AtomicBoolean(false);
    snapshotsColumnFamily.whileTrue(
        (key, value) -> {
          found.set(value.value().getId().equals(snapshotId));
          return !found.get();
        });
    return found.get();
  }

  /** All outbox rows with a seq greater than {@code seq}, in seq order. */
  public List<OutboxRow> outboxAfter(final long seq) {
    final var result = new ArrayList<OutboxRow>();
    outboxKey.wrapLong(seq + 1);
    outboxColumnFamily.whileTrue(
        outboxKey,
        (key, value) -> {
          result.add(new OutboxRow(key.getValue(), value.bytes()));
          return true;
        });
    return result;
  }

  /**
   * The pending checkpoints, i.e. created but not yet covered by a {@code LOG_ARCHIVED} event, with
   * a position up to {@code maxPosition}, in checkpoint id order.
   */
  public List<CoveredCheckpoint> pendingCheckpointsUpTo(final long maxPosition) {
    final var result = new ArrayList<CoveredCheckpoint>();
    pendingCheckpointsColumnFamily.forEach(
        value -> {
          if (value.value().getCheckpointPosition() <= maxPosition) {
            result.add(new CoveredCheckpoint().copy(value.value()));
          }
        });
    return result;
  }

  /**
   * Finds a checkpoint that backup status and listing can report: a pending one, or a backup
   * checkpoint that a {@code LOG_ARCHIVED} event has covered. The latter stays known after its
   * objects are sealed into an index chunk.
   */
  public Optional<CoveredCheckpoint> findCheckpoint(final long checkpointId) {
    pendingCheckpointKey.wrapLong(checkpointId);
    final var pending = pendingCheckpointsColumnFamily.get(pendingCheckpointKey);
    if (pending != null) {
      return Optional.of(new CoveredCheckpoint().copy(pending.value()));
    }
    backupCheckpointKey.wrapLong(checkpointId);
    final var archived = backupCheckpointsColumnFamily.get(backupCheckpointKey);
    return archived == null
        ? Optional.empty()
        : Optional.of(new CoveredCheckpoint().copy(archived.value()));
  }

  /**
   * The pending checkpoints and the covered backup checkpoints with an id greater than {@code
   * checkpointId}, in checkpoint id order.
   */
  public List<CoveredCheckpoint> checkpointsAfter(final long checkpointId) {
    final var result = new TreeMap<Long, CoveredCheckpoint>();
    pendingCheckpointsColumnFamily.forEach(
        value -> {
          if (value.value().getCheckpointId() > checkpointId) {
            result.put(
                value.value().getCheckpointId(), new CoveredCheckpoint().copy(value.value()));
          }
        });
    backupCheckpointKey.wrapLong(checkpointId + 1);
    backupCheckpointsColumnFamily.whileTrue(
        backupCheckpointKey,
        (key, value) -> {
          result.put(key.getValue(), new CoveredCheckpoint().copy(value.value()));
          return true;
        });
    return List.copyOf(result.values());
  }

  /** The covered checkpoints whose type creates a backup, in checkpoint id order. */
  public List<CoveredCheckpoint> backupCheckpoints() {
    final var result = new ArrayList<CoveredCheckpoint>();
    backupCheckpointsColumnFamily.forEach(
        value -> result.add(new CoveredCheckpoint().copy(value.value())));
    return result;
  }

  /** All archived objects above M (without the merged ones) in index order. */
  public List<UnmergedObject> unmergedObjects() {
    final var result = new ArrayList<UnmergedObject>();
    unmergedObjectsColumnFamily.forEach(
        (key, value) ->
            result.add(
                new UnmergedObject(key.getValue(), value.lastIndex(), value.lastPosition())));
    return result;
  }

  /** All merged objects above the released index in index order. */
  public List<LogObject> mergedObjects() {
    final var result = new ArrayList<LogObject>();
    mergedObjectsColumnFamily.forEach(value -> result.add(new LogObject().copy(value.value())));
    return result;
  }

  // ---------------------------------------------------------------------------------------------
  // Mutations: appliers only
  // ---------------------------------------------------------------------------------------------

  public void setMeta(final LogArchiveMeta meta) {
    metaKey.wrapString(META_KEY);
    metaColumnFamily.upsert(metaKey, meta);
  }

  public void insertOpenObject(
      final LogObjectValue object, final List<? extends CoveredCheckpointValue> checkpoints) {
    openObjectKey.wrapLong(object.getFirstIndex());
    openObjectValue.reset();
    openObjectValue.object().copy(object);
    checkpoints.forEach(checkpoint -> openObjectValue.checkpoints().add().copy(checkpoint));
    openObjectsColumnFamily.insert(openObjectKey, openObjectValue);
  }

  public void deleteOpenObject(final long firstIndex) {
    openObjectKey.wrapLong(firstIndex);
    openObjectsColumnFamily.deleteExisting(openObjectKey);
  }

  public void insertChunk(final IndexChunkValue chunk) {
    chunkKey.wrapLong(chunk.getChunkNo());
    chunkValue.value().copy(chunk);
    chunksColumnFamily.insert(chunkKey, chunkValue);
  }

  public void insertSnapshot(final long seq, final ArchivedSnapshotValue snapshot) {
    snapshotKey.wrapLong(seq);
    snapshotValue.value().copy(snapshot);
    snapshotsColumnFamily.insert(snapshotKey, snapshotValue);
  }

  public void insertUnmergedObject(final LogObjectValue object) {
    unmergedObjectKey.wrapLong(object.getFirstIndex());
    unmergedObjectValue.set(object.getLastIndex(), object.getLastPosition());
    unmergedObjectsColumnFamily.insert(unmergedObjectKey, unmergedObjectValue);
  }

  /** Deletes the unmerged objects that start at or below {@code lastIndex}. */
  public void deleteUnmergedObjectsUpTo(final long lastIndex) {
    unmergedObjectsColumnFamily.whileTrue(
        (key, value) -> {
          if (key.getValue() > lastIndex) {
            return false;
          }
          unmergedObjectsColumnFamily.deleteExisting(key);
          return true;
        });
  }

  public void insertMergedObject(final LogObjectValue object) {
    mergedObjectKey.wrapLong(object.getFirstIndex());
    mergedObjectValue.value().copy(object);
    mergedObjectsColumnFamily.insert(mergedObjectKey, mergedObjectValue);
  }

  /** Deletes the merged objects that end at or below {@code lastIndex}. */
  public void deleteMergedObjectsUpTo(final long lastIndex) {
    mergedObjectsColumnFamily.whileTrue(
        (key, value) -> {
          if (value.value().getLastIndex() > lastIndex) {
            return false;
          }
          mergedObjectsColumnFamily.deleteExisting(key);
          return true;
        });
  }

  public void insertOutbox(final long seq, final byte[] commitEntryJson) {
    outboxKey.wrapLong(seq);
    outboxValue.set(commitEntryJson);
    outboxColumnFamily.insert(outboxKey, outboxValue);
  }

  /** Deletes all outbox rows with a seq less than or equal to {@code seq}. */
  public void deleteOutboxUpTo(final long seq) {
    outboxColumnFamily.whileTrue(
        (key, value) -> {
          if (key.getValue() > seq) {
            return false;
          }
          outboxColumnFamily.deleteExisting(key);
          return true;
        });
  }

  /**
   * Records a created checkpoint until a {@code LOG_ARCHIVED} event covers it. Called by the
   * checkpoint {@code CREATED} applier.
   */
  public void insertPendingCheckpoint(
      final long checkpointId,
      final long checkpointPosition,
      final CheckpointType checkpointType,
      final long checkpointTimestamp) {
    pendingCheckpointKey.wrapLong(checkpointId);
    pendingCheckpointValue
        .value()
        .setCheckpointId(checkpointId)
        .setCheckpointPosition(checkpointPosition)
        .setCheckpointType(checkpointType)
        .setCheckpointTimestamp(checkpointTimestamp);
    pendingCheckpointsColumnFamily.upsert(pendingCheckpointKey, pendingCheckpointValue);
  }

  public void deletePendingCheckpoint(final long checkpointId) {
    pendingCheckpointKey.wrapLong(checkpointId);
    pendingCheckpointsColumnFamily.deleteExisting(pendingCheckpointKey);
  }

  /**
   * Records a checkpoint that a {@code LOG_ARCHIVED} event has covered and whose type creates a
   * backup. Called by the {@code LOG_ARCHIVED} applier.
   */
  public void insertBackupCheckpoint(final CoveredCheckpoint checkpoint) {
    backupCheckpointKey.wrapLong(checkpoint.getCheckpointId());
    backupCheckpointValue.value().copy(checkpoint);
    backupCheckpointsColumnFamily.upsert(backupCheckpointKey, backupCheckpointValue);
  }

  /**
   * Deletes every log archive row including the meta row, but neither the pending checkpoints nor
   * the backup checkpoints.
   */
  public void clearAll() {
    clear(metaColumnFamily);
    clear(openObjectsColumnFamily);
    clear(chunksColumnFamily);
    clear(snapshotsColumnFamily);
    clear(outboxColumnFamily);
    clear(unmergedObjectsColumnFamily);
    clear(mergedObjectsColumnFamily);
    // Pending checkpoints are kept: one created before the new timeline starts can still lie in
    // its archived range and is then a valid restore point. Those below the first archived
    // position are dropped when the range is archived.
  }

  private static <K extends DbKey, V extends DbValue> void clear(
      final ColumnFamily<K, V> columnFamily) {
    columnFamily.forEachKey(columnFamily::deleteExisting);
  }

  /**
   * An archived object that is not merged yet: its index range and its last application position.
   */
  public record UnmergedObject(long firstIndex, long lastIndex, long lastPosition) {}

  /** An open object and the checkpoints attached to it. */
  public record OpenObject(LogObject object, List<CoveredCheckpoint> checkpoints) {

    static OpenObject copyOf(final OpenObjectDbValue value) {
      final var checkpoints = new ArrayList<CoveredCheckpoint>();
      value
          .checkpoints()
          .forEach(checkpoint -> checkpoints.add(new CoveredCheckpoint().copy(checkpoint)));
      return new OpenObject(new LogObject().copy(value.object()), List.copyOf(checkpoints));
    }
  }
}
