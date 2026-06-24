/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** RocksDB-backed {@link OffsetState} keyed by {@code (groupId, partitionId) → next position}. */
public final class DbOffsetState implements OffsetState {

  private final DbString groupId = new DbString();
  private final DbInt partitionId = new DbInt();
  private final DbCompositeKey<DbString, DbInt> groupPartitionKey =
      new DbCompositeKey<>(groupId, partitionId);
  private final DbLong offset = new DbLong();

  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbLong> offsetsColumnFamily;

  public DbOffsetState(
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb, final TransactionContext context) {
    offsetsColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_OFFSETS, context, groupPartitionKey, offset);
  }

  @Override
  public long commit(final String groupId, final int partitionId, final long position) {
    this.groupId.wrapString(groupId);
    this.partitionId.wrapInt(partitionId);

    final var existing = offsetsColumnFamily.get(groupPartitionKey);
    final long committed = existing == null ? position : Math.max(existing.getValue(), position);

    offset.wrapLong(committed);
    offsetsColumnFamily.upsert(groupPartitionKey, offset);
    return committed;
  }

  @Override
  public long getOffset(final String groupId, final int partitionId) {
    this.groupId.wrapString(groupId);
    this.partitionId.wrapInt(partitionId);
    final var value = offsetsColumnFamily.get(groupPartitionKey);
    return value == null ? -1L : value.getValue();
  }

  @Override
  public java.util.Map<Integer, Long> getOffsets(final String groupId) {
    this.groupId.wrapString(groupId);
    final var result = new TreeMap<Integer, Long>();
    // Explicit BiConsumer type to disambiguate from the KeyValuePairVisitor overload.
    final BiConsumer<DbCompositeKey<DbString, DbInt>, DbLong> visitor =
        (key, value) -> result.put(key.second().getValue(), value.getValue());
    offsetsColumnFamily.whileEqualPrefix(this.groupId, visitor);
    return result;
  }
}
