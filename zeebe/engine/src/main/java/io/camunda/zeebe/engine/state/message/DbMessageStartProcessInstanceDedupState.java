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
import java.util.ArrayList;
import java.util.List;

/**
 * RocksDB-backed implementation of {@link MutableMessageStartProcessInstanceDedupState}.
 *
 * <p>Two column families back the state:
 *
 * <ul>
 *   <li>{@link ZbColumnFamilies#CROSS_PARTITION_MESSAGE_START_DEDUP} — forward: {@code
 *       (processDefinitionKey, messageKey) → MessageStartProcessInstanceDedupEntry}
 *   <li>{@link ZbColumnFamilies#CROSS_PARTITION_MESSAGE_START_DEDUP_BY_PI_KEY} — reverse: {@code
 *       processInstanceKey → (processDefinitionKey, messageKey)}
 * </ul>
 *
 * The reverse CF lets {@link #tombstoneByProcessInstanceKey(long, long)} locate the forward entry
 * in O(1) when the holder PI completes, without scanning the forward CF by processInstanceKey.
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
      forwardColumnFamily;

  private final DbLong processInstanceKey = new DbLong();
  private final DbLong reverseProcessDefinitionKey = new DbLong();
  private final DbLong reverseMessageKey = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> reverseValue =
      new DbCompositeKey<>(reverseProcessDefinitionKey, reverseMessageKey);
  private final ColumnFamily<DbLong, DbCompositeKey<DbLong, DbLong>> reverseColumnFamily;

  public DbMessageStartProcessInstanceDedupState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    forwardColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_DEDUP,
            transactionContext,
            processDefinitionAndMessageKey,
            entry);
    reverseColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_DEDUP_BY_PI_KEY,
            transactionContext,
            processInstanceKey,
            reverseValue);
  }

  @Override
  public MessageStartProcessInstanceDedupEntry get(
      final long processDefinitionKey, final long messageKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    return forwardColumnFamily.get(
        processDefinitionAndMessageKey, MessageStartProcessInstanceDedupEntry::new);
  }

  @Override
  public void visitTombstonesPastDeadline(final long now, final TombstoneVisitor visitor) {
    // Collect first, then visit, so the visitor is free to mutate the column family (e.g. delete
    // the entry it just observed) without invalidating the iterator.
    final List<long[]> toVisit = new ArrayList<>();
    forwardColumnFamily.forEach(
        (key, value) -> {
          if (value.getStatus() == MessageStartProcessInstanceDedupStatus.TOMBSTONE
              && value.getDeletionDeadline() <= now) {
            toVisit.add(new long[] {key.first().getValue(), key.second().getValue()});
          }
        });
    for (final long[] pair : toVisit) {
      visitor.visit(pair[0], pair[1]);
    }
  }

  @Override
  public void putActive(
      final long processDefinitionKey, final long messageKey, final long processInstanceKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    entry
        .setProcessInstanceKey(processInstanceKey)
        .setStatus(MessageStartProcessInstanceDedupStatus.ACTIVE)
        .setDeletionDeadline(-1L);
    forwardColumnFamily.insert(processDefinitionAndMessageKey, entry);

    this.processInstanceKey.wrapLong(processInstanceKey);
    reverseProcessDefinitionKey.wrapLong(processDefinitionKey);
    reverseMessageKey.wrapLong(messageKey);
    reverseColumnFamily.insert(this.processInstanceKey, reverseValue);
  }

  @Override
  public void tombstoneByProcessInstanceKey(
      final long processInstanceKey, final long deletionDeadline) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    final var forwardKey = reverseColumnFamily.get(this.processInstanceKey);
    if (forwardKey == null) {
      // PI was not created via a cross-partition ask, or the entry has already been deleted.
      return;
    }
    processDefinitionKey.wrapLong(forwardKey.first().getValue());
    messageKey.wrapLong(forwardKey.second().getValue());
    final var existing =
        forwardColumnFamily.get(
            processDefinitionAndMessageKey, MessageStartProcessInstanceDedupEntry::new);
    if (existing == null) {
      // Forward entry vanished between the reverse lookup and the forward read; reverse entry is
      // stale, drop it.
      reverseColumnFamily.deleteExisting(this.processInstanceKey);
      return;
    }
    existing
        .setStatus(MessageStartProcessInstanceDedupStatus.TOMBSTONE)
        .setDeletionDeadline(deletionDeadline);
    forwardColumnFamily.update(processDefinitionAndMessageKey, existing);
  }

  @Override
  public void delete(final long processDefinitionKey, final long messageKey) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageKey.wrapLong(messageKey);
    final var existing =
        forwardColumnFamily.get(
            processDefinitionAndMessageKey, MessageStartProcessInstanceDedupEntry::new);
    if (existing == null) {
      return;
    }
    processInstanceKey.wrapLong(existing.getProcessInstanceKey());
    reverseColumnFamily.deleteIfExists(processInstanceKey);
    forwardColumnFamily.deleteExisting(processDefinitionAndMessageKey);
  }
}
