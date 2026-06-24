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
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * RocksDB-backed {@link MutableOffsetState} keyed by {@code (groupId, partitionId) → position}.
 * Granular storage only — the monotonic (never-rewind) decision is made by the {@code
 * OffsetCommittedApplier}.
 */
public final class DbOffsetState implements MutableOffsetState {

  private final DbString groupId = new DbString();
  private final DbInt partitionId = new DbInt();
  private final DbCompositeKey<DbString, DbInt> groupPartitionKey =
      new DbCompositeKey<>(groupId, partitionId);
  private final DbLong offset = new DbLong();

  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, DbLong> offsetsColumnFamily;

  // Thread-safe mirror of the committed offsets, maintained by putOffset() (which runs on the
  // stream-processing actor via the applier) and read off-actor by the coordinator's heartbeat
  // handler — RocksDB itself is not safe to read from another actor.
  private final Map<String, Map<Integer, Long>> mirror = new ConcurrentHashMap<>();

  public DbOffsetState(
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb, final TransactionContext context) {
    offsetsColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.CONSUMER_OFFSETS, context, groupPartitionKey, offset);
  }

  @Override
  public long getOffset(final String groupId, final int partitionId) {
    this.groupId.wrapString(groupId);
    this.partitionId.wrapInt(partitionId);
    final var value = offsetsColumnFamily.get(groupPartitionKey);
    return value == null ? -1L : value.getValue();
  }

  @Override
  public Map<Integer, Long> offsetsSnapshot(final String groupId) {
    final var offsets = mirror.get(groupId);
    return offsets == null ? Map.of() : new TreeMap<>(offsets);
  }

  @Override
  public void putOffset(final String groupId, final int partitionId, final long position) {
    this.groupId.wrapString(groupId);
    this.partitionId.wrapInt(partitionId);
    offset.wrapLong(position);
    offsetsColumnFamily.upsert(groupPartitionKey, offset);
    mirror
        .computeIfAbsent(groupId, ignored -> new ConcurrentHashMap<>())
        .put(partitionId, position);
  }

  @Override
  public void seedMirror() {
    mirror.clear();
    final BiConsumer<DbCompositeKey<DbString, DbInt>, DbLong> visitor =
        (key, value) ->
            mirror
                .computeIfAbsent(key.first().toString(), ignored -> new ConcurrentHashMap<>())
                .put(key.second().getValue(), value.getValue());
    offsetsColumnFamily.forEach(visitor);
  }
}
