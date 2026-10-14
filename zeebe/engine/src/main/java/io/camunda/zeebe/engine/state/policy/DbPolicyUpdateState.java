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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbPolicyUpdateState implements MutablePolicyUpdateState {

  private final DbLong versionKey = new DbLong();
  private final DbLong currentVersion = new DbLong();
  private final ColumnFamily<DbLong, DbLong> versionColumnFamily;

  private final DbString envelopeId = new DbString();
  private final ColumnFamily<DbString, PersistedPolicyAssembly> assemblyColumnFamily;

  private final DbCompositeKey<DbString, DbLong> chunkKey =
      new DbCompositeKey<>(new DbString(), new DbLong());
  private final PersistedPolicyChunk chunk = new PersistedPolicyChunk();
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedPolicyChunk>
      chunkColumnFamily;

  public DbPolicyUpdateState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    versionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY_VERSION, transactionContext, versionKey, currentVersion);
    versionKey.wrapLong(0);
    assemblyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY_UPDATE_ASSEMBLY,
            transactionContext,
            envelopeId,
            new PersistedPolicyAssembly());
    chunkColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY_UPDATE_CHUNK, transactionContext, chunkKey, chunk);
  }

  @Override
  public long getCurrentVersion() {
    final var version = versionColumnFamily.get(versionKey);
    return version == null ? 0 : version.getValue();
  }

  @Override
  public void setCurrentVersion(final long policyVersion) {
    currentVersion.wrapLong(policyVersion);
    versionColumnFamily.upsert(versionKey, currentVersion);
  }

  @Override
  public Optional<PersistedPolicyAssembly> getAssembly(final String envelopeId) {
    this.envelopeId.wrapString(envelopeId);
    return Optional.ofNullable(
        assemblyColumnFamily.get(this.envelopeId, PersistedPolicyAssembly::new));
  }

  @Override
  public Optional<PersistedPolicyAssembly> findOpenAssembly() {
    final var found = new PersistedPolicyAssembly[1];
    assemblyColumnFamily.whileTrue(
        (key, value) -> {
          final var copy = new PersistedPolicyAssembly();
          copy.copyFrom(value);
          found[0] = copy;
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  @Override
  public boolean hasChunk(final String envelopeId, final int chunkIndex) {
    chunkKey.first().wrapString(envelopeId);
    chunkKey.second().wrapLong(chunkIndex);
    return chunkColumnFamily.exists(chunkKey);
  }

  @Override
  public void appendChunk(final PolicyUpdateRecord record) {
    final var id = record.getEnvelopeId();
    envelopeId.wrapString(id);
    final var assembly = assemblyColumnFamily.get(envelopeId, PersistedPolicyAssembly::new);
    if (assembly == null) {
      assemblyColumnFamily.insert(
          envelopeId,
          new PersistedPolicyAssembly()
              .setEnvelopeId(id)
              .setPolicyVersion(record.getPolicyVersion())
              .setChunkCount(record.getChunkCount())
              .setReceivedCount(1));
    } else {
      assembly.setReceivedCount(assembly.getReceivedCount() + 1);
      assemblyColumnFamily.update(envelopeId, assembly);
    }

    chunkKey.first().wrapString(id);
    chunkKey.second().wrapLong(record.getChunkIndex());
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);
    chunk.setChunk(buffer);
    chunkColumnFamily.insert(chunkKey, chunk);
  }

  @Override
  public void removeAssembly(final String envelopeId) {
    this.envelopeId.wrapString(envelopeId);
    final var assembly = assemblyColumnFamily.get(this.envelopeId, PersistedPolicyAssembly::new);
    if (assembly == null) {
      return;
    }
    for (int index = 0; index < assembly.getChunkCount(); index++) {
      chunkKey.first().wrapString(envelopeId);
      chunkKey.second().wrapLong(index);
      chunkColumnFamily.deleteIfExists(chunkKey);
    }
    assemblyColumnFamily.deleteExisting(this.envelopeId);
  }

  @Override
  public PolicyUpdateRecord getChunk(final String envelopeId, final int chunkIndex) {
    chunkKey.first().wrapString(envelopeId);
    chunkKey.second().wrapLong(chunkIndex);
    final var stored = chunkColumnFamily.get(chunkKey, PersistedPolicyChunk::new);
    if (stored == null) {
      throw new IllegalStateException(
          "Expected chunk %d of update '%s' to be stored, but it was not found"
              .formatted(chunkIndex, envelopeId));
    }
    final var record = new PolicyUpdateRecord();
    record.wrap(stored.getChunk());
    return record;
  }

  @Override
  public void markChunkApplied(
      final String envelopeId,
      final long policyVersion,
      final int chunkIndex,
      final boolean lastPage) {
    if (lastPage) {
      currentVersion.wrapLong(policyVersion);
      versionColumnFamily.upsert(versionKey, currentVersion);
      removeAssembly(envelopeId);
      return;
    }
    this.envelopeId.wrapString(envelopeId);
    final var assembly = assemblyColumnFamily.get(this.envelopeId, PersistedPolicyAssembly::new);
    assembly.setAppliedChunks(chunkIndex + 1);
    assemblyColumnFamily.update(this.envelopeId, assembly);
  }
}
