/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.offset;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.state.mutable.MutableOffsetState;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;

/**
 * RocksDB-backed {@link MutableOffsetState} keyed by {@code (groupId, topic, partitionId) →
 * position}, used on the stream-processing actor by the offset applier/processor. Granular storage
 * only — the monotonic (never-rewind) decision is made by the {@code OffsetCommittedApplier}.
 *
 * <p>There is deliberately no in-memory mirror: offsets are unbounded, so off-actor reads go to
 * state through {@link OffsetQueryService} (a separate context) instead of a heap projection.
 */
public final class DbOffsetState implements MutableOffsetState {

  private final DbString groupId = new DbString();
  private final DbString topic = new DbString();
  private final DbInt partitionId = new DbInt();
  // (groupId, (topic, partitionId)) — a group's offsets share the groupId prefix.
  private final DbCompositeKey<DbString, DbInt> topicPartitionKey =
      new DbCompositeKey<>(topic, partitionId);
  private final DbCompositeKey<DbString, DbCompositeKey<DbString, DbInt>> offsetKey =
      new DbCompositeKey<>(groupId, topicPartitionKey);
  private final DbLong offset = new DbLong();

  private final ColumnFamily<DbCompositeKey<DbString, DbCompositeKey<DbString, DbInt>>, DbLong>
      offsetsColumnFamily;

  public DbOffsetState(
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb, final TransactionContext context) {
    offsetsColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_OFFSETS, context, offsetKey, offset);
  }

  @Override
  public long getOffset(final String groupId, final String topic, final int partitionId) {
    this.groupId.wrapString(groupId);
    this.topic.wrapString(topic);
    this.partitionId.wrapInt(partitionId);
    final var value = offsetsColumnFamily.get(offsetKey);
    return value == null ? -1L : value.getValue();
  }

  @Override
  public void putOffset(
      final String groupId, final String topic, final int partitionId, final long position) {
    this.groupId.wrapString(groupId);
    this.topic.wrapString(topic);
    this.partitionId.wrapInt(partitionId);
    offset.wrapLong(position);
    offsetsColumnFamily.upsert(offsetKey, offset);
  }
}
