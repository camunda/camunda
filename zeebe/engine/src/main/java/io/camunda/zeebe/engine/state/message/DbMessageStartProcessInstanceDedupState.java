/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.collections.MutableBoolean;

/**
 * RocksDB-backed implementation of {@link MutableMessageStartProcessInstanceDedupState}.
 *
 * <p>A single column family — {@link ZbColumnFamilies#CROSS_PARTITION_MESSAGE_START_DEDUP} — holds
 * {@code (processDefinitionKey, messageKey) → MessageStartProcessInstanceDedupEntry}. The entry
 * carries the originally-replied {@code processInstanceKey} and a {@code deletionDeadline} (epoch
 * millis) taken directly from the request's {@code messageDeadline} (= {@code publishTime + ttl} on
 * {@code P_K}), so the dedup row on {@code P_B} and the buffered message on {@code P_K} share the
 * same lifetime without any engine-internal time coupling.
 *
 * <p>The retention contract is intentionally PI-lifecycle-free: the row exists to bound {@code
 * P_K}'s retry window, and {@code P_K}'s retry scheduler enforces {@code retryDeadline <=
 * messageDeadline} via the {@link
 * io.camunda.zeebe.engine.state.appliers.MessageExpiredApplier}-driven pending-ask cleanup. There
 * is no PI-completion hook on this state and no reverse mapping by process-instance key — both are
 * unnecessary under write-time expiry.
 */
public final class DbMessageStartProcessInstanceDedupState
    implements MutableMessageStartProcessInstanceDedupState {

  private final DbLong processDefinitionKey = new DbLong();
  private final DbLong messageKey = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> processDefinitionAndMessageKey =
      new DbCompositeKey<>(processDefinitionKey, messageKey);
  private final MessageStartProcessInstanceDedupEntry entry =
      new MessageStartProcessInstanceDedupEntry();
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, MessageStartProcessInstanceDedupEntry>
      columnFamily;

  public DbMessageStartProcessInstanceDedupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_DEDUP,
            transactionContext,
            processDefinitionAndMessageKey,
            entry);
  }

  @Override
  public MessageStartProcessInstanceDedupEntry get(
      final long processDefinitionKey, final long messageKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    return columnFamily.get(
        processDefinitionAndMessageKey, MessageStartProcessInstanceDedupEntry::new);
  }

  @Override
  public boolean visitExpiredEntries(final long now, final ExpiredEntryVisitor visitor) {
    // Iterate the CF directly with early-exit so per-tick work is bounded by the visitor's batch
    // limit rather than the full CF size. The visitor must not mutate the column family — any
    // deletions are deferred to event appliers that run after this call returns.
    final var stoppedEarly = new MutableBoolean(false);
    columnFamily.whileTrue(
        (key, value) -> {
          if (value.getDeletionDeadline() > now) {
            // Keep scanning: ordering is by (processDefinitionKey, messageKey), not deadline.
            return true;
          }
          if (!visitor.visit(key.first().getValue(), key.second().getValue())) {
            stoppedEarly.set(true);
            return false;
          }
          return true;
        });
    return stoppedEarly.get();
  }

  @Override
  public boolean hasExpiredEntry(final long now) {
    final var found = new MutableBoolean(false);
    columnFamily.whileTrue(
        (key, value) -> {
          if (value.getDeletionDeadline() <= now) {
            found.set(true);
            return false;
          }
          return true;
        });
    return found.get();
  }

  @Override
  public void put(
      final long processDefinitionKey,
      final long messageKey,
      final long processInstanceKey,
      final long deletionDeadline) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    entry.setProcessInstanceKey(processInstanceKey).setDeletionDeadline(deletionDeadline);
    columnFamily.upsert(processDefinitionAndMessageKey, entry);
  }

  @Override
  public void delete(final long processDefinitionKey, final long messageKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    columnFamily.deleteIfExists(processDefinitionAndMessageKey);
  }
}
