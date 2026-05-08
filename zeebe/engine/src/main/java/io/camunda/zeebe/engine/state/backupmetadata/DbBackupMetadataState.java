/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.backupmetadata;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableBackupMetadataState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * RocksDB-backed implementation of {@link MutableBackupMetadataState}. Rows are stored in {@link
 * ZbColumnFamilies#BACKUP_METADATA} keyed by a composite {@code (checkpointId, partitionId)} and
 * the value is the msgpack-encoded {@link BackupMetadataRecord}.
 *
 * <p>The {@code checkpointId} comes first so a prefix scan over a single id ({@link
 * #iterateByCheckpoint}) iterates exactly the rows for that checkpoint.
 *
 * <p>Implementation note: the on-disk value is stored as opaque {@link DbBytes} (the serialized
 * record bytes) rather than the record directly, because {@link BackupMetadataRecord} extends
 * {@code UnifiedRecordValue} which does not implement {@code DbValue}. Decoding is performed inline
 * on read; writes serialize through an internal scratch buffer.
 */
public final class DbBackupMetadataState implements MutableBackupMetadataState {

  private final DbLong checkpointId = new DbLong();
  private final DbInt partitionId = new DbInt();
  private final DbCompositeKey<DbLong, DbInt> compositeKey =
      new DbCompositeKey<>(checkpointId, partitionId);

  private final DbBytes valueBytes = new DbBytes();
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbBytes> column;

  private final ExpandableArrayBuffer scratch = new ExpandableArrayBuffer();

  public DbBackupMetadataState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    column =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BACKUP_METADATA, transactionContext, compositeKey, valueBytes);
  }

  @Override
  public BackupMetadataRecord get(final long checkpointId, final int partitionId) {
    this.checkpointId.wrapLong(checkpointId);
    this.partitionId.wrapInt(partitionId);
    final DbBytes stored = column.get(compositeKey);
    if (stored == null) {
      return null;
    }
    return decode(stored);
  }

  @Override
  public void iterateByCheckpoint(
      final long checkpointId, final Consumer<BackupMetadataRecord> consumer) {
    this.checkpointId.wrapLong(checkpointId);
    final BiConsumer<DbCompositeKey<DbLong, DbInt>, DbBytes> visitor =
        (key, value) -> consumer.accept(decode(value));
    column.whileEqualPrefix(this.checkpointId, visitor);
  }

  @Override
  public void iterateAll(final Consumer<BackupMetadataRecord> consumer) {
    column.forEach((key, value) -> consumer.accept(decode(value)));
  }

  @Override
  public void put(final BackupMetadataRecord record) {
    checkpointId.wrapLong(record.getCheckpointId());
    partitionId.wrapInt(record.getPartitionId());
    final int length = record.getLength();
    record.write(scratch, 0);
    final byte[] bytes = new byte[length];
    scratch.getBytes(0, bytes);
    valueBytes.wrapBytes(bytes);
    column.upsert(compositeKey, valueBytes);
  }

  @Override
  public void delete(final long checkpointId, final int partitionId) {
    this.checkpointId.wrapLong(checkpointId);
    this.partitionId.wrapInt(partitionId);
    column.deleteIfExists(compositeKey);
  }

  private static BackupMetadataRecord decode(final DbBytes stored) {
    final var direct = stored.getDirectBuffer();
    final byte[] copy = new byte[direct.capacity()];
    direct.getBytes(0, copy);
    final BackupMetadataRecord record = new BackupMetadataRecord();
    record.wrap(new UnsafeBuffer(copy), 0, copy.length);
    return record;
  }
}
