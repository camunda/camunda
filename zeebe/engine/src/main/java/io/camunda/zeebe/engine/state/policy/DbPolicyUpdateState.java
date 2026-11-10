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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
    final var openUpdateId = new AtomicReference<String>();
    updateColumnFamily.whileTrue(
        key -> {
          openUpdateId.set(key.toString());
          return false;
        });
    if (openUpdateId.get() == null) {
      return Optional.empty();
    }
    updateId.wrapString(openUpdateId.get());
    return Optional.ofNullable(updateColumnFamily.get(updateId, PersistedPolicyUpdate::new));
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
    update.setBufferedChunkCount(update.getBufferedChunkCount() + 1);
    updateColumnFamily.upsert(updateId, update);

    chunkIndex.wrapInt(chunk.getChunkIndex());
    chunkColumnFamily.insert(updateIdAndChunkIndex, bufferedChunk.setChunk(chunk));
  }

  @Override
  public void removeUpdate(final String updateId) {
    this.updateId.wrapString(updateId);
    chunkColumnFamily.whileEqualPrefix(
        this.updateId,
        (final DbCompositeKey<DbString, DbInt> key, final PersistedPolicyUpdateChunk chunk) ->
            chunkColumnFamily.deleteExisting(key));
    updateColumnFamily.deleteIfExists(this.updateId);
  }
}
