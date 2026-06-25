/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.broker;

import io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.mutable.MutableBrokerState;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RocksDB-backed {@link MutableBrokerState}: the broker registry keyed by {@code brokerId} → {@link
 * PersistedBroker}. Rebuilt identically on every replica via stream replay, so a new metadata
 * leader restores the broker set (and their epochs/liveness state) after failover.
 *
 * <p>The appliers are the only writers; reads come straight from the column family, so there is no
 * in-memory mirror. The flyweight key/value belong to one {@link TransactionContext}, so an
 * instance is single-actor: the stream processor holds one, and each off-actor reader gets its own
 * through a {@link BrokerQueryService}. {@link #brokersSnapshot}/{@link #activeBrokers} pin their
 * results (copy out of the flyweights) so a caller may keep them after the next read mutates them.
 */
public final class DbBrokerState implements MutableBrokerState {

  private final DbInt brokerId = new DbInt();
  private final PersistedBroker persistedBroker = new PersistedBroker();
  private final ColumnFamily<DbInt, PersistedBroker> brokerColumnFamily;

  public DbBrokerState(
      final ZeebeDb<MetadataColumnFamilies> zeebeDb, final TransactionContext context) {
    brokerColumnFamily =
        zeebeDb.createColumnFamily(
            MetadataColumnFamilies.BROKER_REGISTRY, context, brokerId, persistedBroker);
  }

  @Override
  public BrokerMetadata get(final int id) {
    brokerId.wrapInt(id);
    final var stored = brokerColumnFamily.get(brokerId);
    return stored == null ? null : stored.toMetadata(id);
  }

  @Override
  public Map<Integer, BrokerMetadata> brokersSnapshot() {
    final var snapshot = new LinkedHashMap<Integer, BrokerMetadata>();
    brokerColumnFamily.forEach(
        (key, value) -> {
          final var id = key.getValue();
          snapshot.put(id, value.toMetadata(id));
        });
    return snapshot;
  }

  @Override
  public List<Integer> activeBrokers() {
    final var active = new ArrayList<Integer>();
    brokerColumnFamily.forEach(
        (key, value) -> {
          if (value.toMetadata(key.getValue()).status() == BrokerStatus.ACTIVE) {
            active.add(key.getValue());
          }
        });
    active.sort(Integer::compareTo);
    return active;
  }

  @Override
  public void put(final int id, final BrokerMetadata metadata) {
    brokerId.wrapInt(id);
    brokerColumnFamily.upsert(brokerId, persistedBroker.wrap(metadata));
  }

  @Override
  public void delete(final int id) {
    brokerId.wrapInt(id);
    brokerColumnFamily.deleteIfExists(brokerId);
  }
}
