/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.routing;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DbRoutingState implements MutableRoutingState {

  private static final String CURRENT_KEY = "CURRENT";
  private static final String DESIRED_KEY = "DESIRED";

  private final ColumnFamily<DbString, PersistedRoutingInfo> columnFamily;
  private final ColumnFamily<DbInt, DbLong> bootstrappedAtColumnFamily;
  private final DbString key = new DbString();
  private final DbInt partitionIdKey = new DbInt();
  private final DbLong dbLong = new DbLong();
  private final PersistedRoutingInfo currentRoutingInfo = new PersistedRoutingInfo();
  private final PersistedRoutingInfo desiredRoutingInfo = new PersistedRoutingInfo();

  public DbRoutingState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROUTING, transactionContext, key, new PersistedRoutingInfo());
    bootstrappedAtColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BOOTSTRAPPED_AT, transactionContext, partitionIdKey, new DbLong());
  }

  @Override
  public Set<Integer> currentPartitions() {
    key.wrapString(CURRENT_KEY);
    return columnFamily.get(key).getPartitions();
  }

  @Override
  public Set<Integer> desiredPartitions() {
    key.wrapString(DESIRED_KEY);
    final var desiredRoutingInfo = columnFamily.get(key);
    if (desiredRoutingInfo == null) {
      return Set.of();
    }
    return desiredRoutingInfo.getPartitions();
  }

  @Override
  public MessageCorrelation messageCorrelation() {
    key.wrapString(CURRENT_KEY);
    return columnFamily.get(key).getMessageCorrelation();
  }

  @Override
  public boolean isInitialized() {
    key.wrapString(CURRENT_KEY);
    return columnFamily.exists(key);
  }

  @Override
  public long bootstrappedAt(final int partitionCount) {
    partitionIdKey.wrapInt(partitionCount);
    final var value = bootstrappedAtColumnFamily.get(partitionIdKey);
    if (value == null) {
      return -1L;
    }
    return value.getValue();
  }

  @Override
  public void initializeRoutingInfo(final int partitionCount) {
    final var partitions =
        IntStream.rangeClosed(Protocol.START_PARTITION_ID, partitionCount)
            .boxed()
            .collect(Collectors.toCollection(TreeSet::new));

    key.wrapString(CURRENT_KEY);
    currentRoutingInfo.reset();
    currentRoutingInfo.setPartitions(partitions);
    currentRoutingInfo.setMessageCorrelation(new MessageCorrelation.HashMod(partitionCount));
    columnFamily.insert(key, currentRoutingInfo);
    setDesiredPartitions(partitions, 0L);
  }

  @Override
  public void setDesiredPartitions(final Set<Integer> partitions, final long eventKey) {
    final var currentMessageCorrelation = messageCorrelation();
    desiredRoutingInfo.reset();
    desiredRoutingInfo.setPartitions(new TreeSet<>(partitions));
    desiredRoutingInfo.setMessageCorrelation(currentMessageCorrelation);

    setBootstrappedAt(partitions.size(), eventKey);
    key.wrapString(DESIRED_KEY);
    columnFamily.upsert(key, desiredRoutingInfo);
  }

  @Override
  public boolean activatePartition(final int partitionId) {
    key.wrapString(DESIRED_KEY);
    final var desiredState = columnFamily.get(key);
    if (desiredState.getPartitions().contains(partitionId)) {
      key.wrapString(CURRENT_KEY);
      final var current = columnFamily.get(key);
      final var newPartitions = new TreeSet<>(current.getPartitions());
      newPartitions.add(partitionId);
      current.setPartitions(newPartitions);
      columnFamily.update(key, current);
      return newPartitions.equals(desiredState.getPartitions());
    } else {
      return false;
    }
  }

  private void setBootstrappedAt(final int partitionCount, final long key) {
    partitionIdKey.wrapInt(partitionCount);
    dbLong.wrapLong(key);
    if (bootstrappedAtColumnFamily.get(partitionIdKey) == null) {
      // do not override if it's already set
      bootstrappedAtColumnFamily.insert(partitionIdKey, dbLong);
    }
  }
}
