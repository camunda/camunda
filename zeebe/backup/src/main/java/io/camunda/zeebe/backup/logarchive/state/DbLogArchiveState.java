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
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.ArchivedSnapshotValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.CoveredCheckpointValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.IndexChunkValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import java.util.ArrayList;
import java.util.List;
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

  /** Deletes every log archive row including the meta row. */
  public void clearAll() {
    clear(metaColumnFamily);
    clear(openObjectsColumnFamily);
    clear(chunksColumnFamily);
    clear(snapshotsColumnFamily);
    clear(outboxColumnFamily);
  }

  private static <K extends DbKey, V extends DbValue> void clear(
      final ColumnFamily<K, V> columnFamily) {
    columnFamily.forEachKey(columnFamily::deleteExisting);
  }

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
