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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableRoutingState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DbRoutingState implements MutableRoutingState {

  /**
   * This state will later be extended to hold current and <i>desired</i> routing information. This
   * is the key for the current routing information.
   */
  private static final String CURRENT_KEY = "CURRENT";

  private final ColumnFamily<DbString, PersistedRoutingInfo> columnFamily;
  private final DbString key = new DbString();
  private final PersistedRoutingInfo currentRoutingInfo = new PersistedRoutingInfo();

  public DbRoutingState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ROUTING, transactionContext, key, currentRoutingInfo);
  }

  @Override
  public Set<Integer> partitions() {
    key.wrapString(CURRENT_KEY);
    return columnFamily.get(key).getPartitions();
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
  public void initializeRoutingInfo(final int partitionCount) {
    final var partitions =
        IntStream.rangeClosed(1, partitionCount).boxed().collect(Collectors.toUnmodifiableSet());

    key.wrapString(CURRENT_KEY);
    currentRoutingInfo.reset();
    currentRoutingInfo.setPartitions(partitions);
    currentRoutingInfo.setMessageCorrelation(new MessageCorrelation.HashMod(partitionCount));
    columnFamily.insert(key, currentRoutingInfo);
  }
}
