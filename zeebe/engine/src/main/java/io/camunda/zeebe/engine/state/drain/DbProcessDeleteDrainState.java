/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.drain;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableProcessDeleteDrainState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.collections.MutableBoolean;

public final class DbProcessDeleteDrainState implements MutableProcessDeleteDrainState {

  private final DbLong processDefinitionKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> processDefinitionPartitionKey;

  /** [process definition key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil>
      drainingProcessDeleteColumnFamily;

  public DbProcessDeleteDrainState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processDefinitionKey = new DbLong();
    partitionKey = new DbInt();
    processDefinitionPartitionKey = new DbCompositeKey<>(processDefinitionKey, partitionKey);
    drainingProcessDeleteColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DRAINING_PROCESS_DELETE,
            transactionContext,
            processDefinitionPartitionKey,
            DbNil.INSTANCE);
  }

  @Override
  public void addDrainingPartition(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    partitionKey.wrapInt(partitionId);
    drainingProcessDeleteColumnFamily.upsert(processDefinitionPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public void removeDrainingPartition(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    partitionKey.wrapInt(partitionId);
    drainingProcessDeleteColumnFamily.deleteIfExists(processDefinitionPartitionKey);
  }

  @Override
  public boolean hasDrainingPartition(final long processDefinitionKey, final int partitionId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    partitionKey.wrapInt(partitionId);
    return drainingProcessDeleteColumnFamily.exists(processDefinitionPartitionKey);
  }

  @Override
  public boolean hasDrainingPartition(final long processDefinitionKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    final var hasDraining = new MutableBoolean();
    drainingProcessDeleteColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        ignored -> {
          hasDraining.set(true);
          return false;
        });
    return hasDraining.get();
  }
}
