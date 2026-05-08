/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clusterconfiguration;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.state.mutable.MutableClusterConfigurationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * RocksDB-backed implementation of {@link MutableClusterConfigurationState}.
 *
 * <p>The state is keyed by a single fixed string {@value #CURRENT_KEY}; the value is the
 * proto-encoded {@link ClusterConfiguration} bytes. Encoding is delegated to {@link
 * ProtoBufSerializer} so that the on-disk representation matches what's gossiped on the wire.
 *
 * <p>An in-memory cache mirrors the persisted value for cheap reads on the actor thread. Writes
 * always go through {@link #put(ClusterConfiguration)}, which updates RocksDB first and then
 * refreshes the cache.
 */
public final class DbClusterConfigurationState implements MutableClusterConfigurationState {

  private static final String CURRENT_KEY = "current";

  private final ColumnFamily<DbString, DbBytes> column;
  private final DbString key = new DbString();
  private final DbBytes value = new DbBytes();
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  private ClusterConfiguration cached = ClusterConfiguration.uninitialized();

  public DbClusterConfigurationState(
      final ZeebeDb<ZbColumnFamilies> db, final TransactionContext ctx) {
    column = db.createColumnFamily(ZbColumnFamilies.CLUSTER_CONFIGURATION, ctx, key, value);
    key.wrapString(CURRENT_KEY);
    final DbBytes existing = column.get(key);
    if (existing != null) {
      final byte[] bytes = copy(existing);
      cached = serializer.decodeClusterTopology(bytes, 0, bytes.length);
    }
  }

  @Override
  public ClusterConfiguration get() {
    return cached;
  }

  @Override
  public void put(final ClusterConfiguration config) {
    key.wrapString(CURRENT_KEY);
    value.wrapBytes(serializer.encode(config));
    column.upsert(key, value);
    cached = config;
  }

  private static byte[] copy(final DbBytes existing) {
    final var direct = existing.getDirectBuffer();
    final byte[] out = new byte[direct.capacity()];
    direct.getBytes(0, out);
    return out;
  }
}
