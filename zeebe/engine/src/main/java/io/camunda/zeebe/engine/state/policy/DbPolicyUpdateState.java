/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DbPolicyUpdateState implements MutablePolicyUpdateState {

  private final DbString updateId = new DbString();
  private final DbInt chunkIndex = new DbInt();
  private final DbCompositeKey<DbString, DbInt> updateIdAndChunkIndex =
      new DbCompositeKey<>(updateId, chunkIndex);
  private final PersistedPolicyUpdateChunk bufferedChunk = new PersistedPolicyUpdateChunk();

  private final ColumnFamily<DbString, PersistedPolicyUpdate> updateColumnFamily;
  private final ColumnFamily<DbCompositeKey<DbString, DbInt>, PersistedPolicyUpdateChunk>
      chunkColumnFamily;

  public DbPolicyUpdateState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    updateColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY_UPDATE,
            transactionContext,
            updateId,
            new PersistedPolicyUpdate());
    chunkColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY_UPDATE_CHUNK,
            transactionContext,
            updateIdAndChunkIndex,
            bufferedChunk);
  }

  @Override
  public Optional<PersistedPolicyUpdate> getOpenUpdate() {
    final var openUpdate = new PersistedPolicyUpdate();
    final var found = new boolean[1];
    updateColumnFamily.whileTrue(
        (key, update) -> {
          BufferUtil.copy(update, openUpdate);
          found[0] = true;
          return false;
        });
    return found[0] ? Optional.of(openUpdate) : Optional.empty();
  }

  @Override
  public boolean hasChunk(final String updateId, final int chunkIndex) {
    this.updateId.wrapString(updateId);
    this.chunkIndex.wrapInt(chunkIndex);
    return chunkColumnFamily.exists(updateIdAndChunkIndex);
  }

  @Override
  public Optional<PolicyUpdateRecord> getChunk(final String updateId, final int chunkIndex) {
    this.updateId.wrapString(updateId);
    this.chunkIndex.wrapInt(chunkIndex);
    return Optional.ofNullable(
            chunkColumnFamily.get(updateIdAndChunkIndex, PersistedPolicyUpdateChunk::new))
        .map(PersistedPolicyUpdateChunk::getChunk);
  }

  @Override
  public void bufferChunk(final PolicyUpdateRecord chunk) {
    updateId.wrapString(chunk.getUpdateId());
    final var update =
        Optional.ofNullable(updateColumnFamily.get(updateId, PersistedPolicyUpdate::new))
            .orElseGet(() -> new PersistedPolicyUpdate().setUpdate(chunk));
    chunkIndex.wrapInt(chunk.getChunkIndex());
    if (!chunkColumnFamily.exists(updateIdAndChunkIndex)) {
      update.setBufferedChunkCount(update.getBufferedChunkCount() + 1);
    }
    updateColumnFamily.upsert(updateId, update);
    chunkColumnFamily.upsert(updateIdAndChunkIndex, bufferedChunk.setChunk(chunk));
  }

  @Override
  public void removeAll() {
    chunkColumnFamily.whileTrue(
        (key, chunk) -> {
          chunkColumnFamily.deleteExisting(key);
          return true;
        });
    updateColumnFamily.whileTrue(
        (key, update) -> {
          updateColumnFamily.deleteExisting(key);
          return true;
        });
  }
}
