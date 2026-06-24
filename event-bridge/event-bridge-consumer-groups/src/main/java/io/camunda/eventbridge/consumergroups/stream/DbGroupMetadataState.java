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
import io.camunda.zeebe.db.impl.DbString;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * RocksDB-backed replicated consumer-group metadata, keyed by {@code groupId} → encoded {@link
 * GroupMetadataCodec} payload. Rebuilt identically on every replica via stream replay, so a new
 * coordinator leader can restore membership after failover.
 */
public final class DbGroupMetadataState {

  private final DbString groupId = new DbString();
  private final DbString payload = new DbString();
  private final ColumnFamily<DbString, DbString> metadataColumnFamily;

  public DbGroupMetadataState(
      final ZeebeDb<EventBridgeColumnFamilies> zeebeDb, final TransactionContext context) {
    metadataColumnFamily =
        zeebeDb.createColumnFamily(
            EventBridgeColumnFamilies.GROUP_METADATA, context, groupId, payload);
  }

  public void put(final String groupId, final String payload) {
    this.groupId.wrapString(groupId);
    this.payload.wrapString(payload);
    metadataColumnFamily.upsert(this.groupId, this.payload);
  }

  /** Returns a group's encoded metadata, or {@code null} if the group is unknown. */
  public String get(final String groupId) {
    this.groupId.wrapString(groupId);
    final var stored = metadataColumnFamily.get(this.groupId);
    return stored == null ? null : stored.toString();
  }

  /** Returns all groups' encoded metadata ({@code groupId → payload}) for failover rebuild. */
  public Map<String, String> readAll() {
    final var result = new LinkedHashMap<String, String>();
    final BiConsumer<DbString, DbString> visitor =
        (key, value) -> result.put(key.toString(), value.toString());
    metadataColumnFamily.forEach(visitor);
    return result;
  }
}
