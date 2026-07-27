/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.ordinal;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalState;
import io.camunda.zeebe.engine.state.ordinal.PersistedOrdinal.OrdinalStatus;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;

public class DbOrdinalState implements MutableOrdinalState {

  // key to identify the currently active ordinal
  private static final String ACTIVE_ORDINAL_KEY_KEY = "ACTIVE_ORDINAL_KEY";

  /** Stores the active ordinal key which we can use to get it's ordinal state by key. */
  private final ColumnFamily<DbString, DbInt> activeOrdinalColumnFamily;

  private final DbString activeOrdinalKey = new DbString();
  private final DbInt activeOrdinalValue = new DbInt();

  /** Stores ordinal states by ordinal key & partitionId */
  private final ColumnFamily<DbCompositeKey<DbInt, DbInt>, PersistedOrdinal>
      ordinalStatesColumnFamily;

  private final DbCompositeKey<DbInt, DbInt> ordinalAndPartitionKey =
      new DbCompositeKey<>(new DbInt(), new DbInt());
  private final PersistedOrdinal persistedOrdinal = new PersistedOrdinal();

  public DbOrdinalState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    // active ordinal key index
    activeOrdinalColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ORDINAL_ACTIVE_STATE,
            transactionContext,
            activeOrdinalKey,
            activeOrdinalValue);

    // ordinal states by ordinal key & partitionId
    ordinalStatesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ORDINAL_STATE,
            transactionContext,
            ordinalAndPartitionKey,
            persistedOrdinal);
  }

  @Override
  public boolean isInitialized() {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    final var activeOrdinalKey = activeOrdinalColumnFamily.get(this.activeOrdinalKey);
    return activeOrdinalKey != null;
  }

  @Override
  public int getActiveOrdinalKey() {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    final var activeOrdinalKey = activeOrdinalColumnFamily.get(this.activeOrdinalKey);
    if (activeOrdinalKey == null) {
      // 0-1000 are reserved ordinal keys,
      // using 101 as the default destination index when ordinals not initialized
      return 101;
    }
    return activeOrdinalKey.getValue();
  }

  @Override
  public void activate(final int ordinalKey) {
    activeOrdinalKey.wrapString(ACTIVE_ORDINAL_KEY_KEY);
    activeOrdinalValue.wrapInt(ordinalKey);
    activeOrdinalColumnFamily.upsert(activeOrdinalKey, activeOrdinalValue);
  }

  @Override
  public Optional<PersistedOrdinal> getOrdinalStateById(
      final int ordinalKey, final int partitionId) {
    return Optional.ofNullable(
        ordinalStatesColumnFamily.get(
            getOrdinalAndPartitionKey(ordinalKey, partitionId), PersistedOrdinal::new));
  }

  @Override
  public void createOrdinalState(final int ordinalKey, final int partitionId) {
    final PersistedOrdinal pendingOrdinal =
        new PersistedOrdinal().setOrdinalKey(ordinalKey).setStatus(OrdinalStatus.PENDING);

    final var ordinalAndPartitionKey = getOrdinalAndPartitionKey(ordinalKey, partitionId);

    if (!ordinalStatesColumnFamily.exists(ordinalAndPartitionKey)) {
      ordinalStatesColumnFamily.insert(ordinalAndPartitionKey, pendingOrdinal);
    }
  }

  @Override
  public void activate(final int ordinalKey, final int partitionId) {}

  private DbCompositeKey<DbInt, DbInt> getOrdinalAndPartitionKey(
      final int ordinalKey, final int partitionId) {
    final DbInt ordinalKeyKey = new DbInt();
    ordinalKeyKey.wrapInt(ordinalKey);

    final DbInt partitionIdKey = new DbInt();
    ordinalKeyKey.wrapInt(ordinalKey);

    return new DbCompositeKey<>(ordinalKeyKey, partitionIdKey);
  }
}
