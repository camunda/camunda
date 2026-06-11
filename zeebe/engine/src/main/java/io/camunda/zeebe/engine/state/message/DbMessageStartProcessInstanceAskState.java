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
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState.PendingAskKey;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.util.ArrayList;
import java.util.List;

/**
 * RocksDB-backed implementation of {@link MutableMessageStartProcessInstanceAskState}.
 *
 * <p>The CF is keyed by {@code (messageKey, processDefinitionKey)} and stores a {@link
 * MessageStartProcessInstanceAsk}.
 *
 * <p>The transient last-sent-timestamp tracker ({@link
 * TransientPendingMessageStartProcessInstanceAskState}) is rebuilt from the CF on recovery — all
 * entries start with timestamp 0, making them immediately eligible for re-send. This is safe
 * because the success-only dedup on {@code P_B} bounds the storm.
 */
public final class DbMessageStartProcessInstanceAskState
    implements MutableMessageStartProcessInstanceAskState, StreamProcessorLifecycleAware {

  /**
   * Upper bound on the persisted rejection count. The scheduler derives the back-off interval as
   * {@code min(baseInterval * 2^rejectionCount, cap)}, which saturates at the cap long before this
   * bound; capping the stored count keeps the value bounded for a very long-blocked ask and avoids
   * any overflow when the scheduler computes {@code 2^rejectionCount}.
   */
  private static final long MAX_REJECTION_COUNT = 30L;

  private final DbLong messageKey = new DbLong();
  private final DbLong processDefinitionKey = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> key =
      new DbCompositeKey<>(messageKey, processDefinitionKey);
  private final MessageStartProcessInstanceAsk value = new MessageStartProcessInstanceAsk();
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, MessageStartProcessInstanceAsk>
      columnFamily;
  private final TransientPendingMessageStartProcessInstanceAskState transientState;

  public DbMessageStartProcessInstanceAskState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final TransientPendingMessageStartProcessInstanceAskState transientState) {
    this.transientState = transientState;
    columnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CROSS_PARTITION_MESSAGE_START_ASK, transactionContext, key, value);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // Rebuild the transient state from the persisted CF. All entries are added with lastSentTime=0
    // so they are immediately eligible for re-send; the P_B dedup bounds the storm.
    columnFamily.forEach(
        (k, v) ->
            transientState.add(new PendingAskKey(k.first().getValue(), k.second().getValue()), 0L));
  }

  @Override
  public MessageStartProcessInstanceAsk get(
      final long messageKey, final long processDefinitionKey) {
    this.messageKey.wrapLong(messageKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    return columnFamily.get(key, MessageStartProcessInstanceAsk::new);
  }

  @Override
  public void forEach(final AskVisitor visitor) {
    columnFamily.forEach((k, v) -> visitor.visit(k.first().getValue(), k.second().getValue(), v));
  }

  @Override
  public boolean hasPendingAsksPastDeadline(final long deadline) {
    return transientState.entriesBefore(deadline).iterator().hasNext();
  }

  @Override
  public Iterable<MessageStartProcessInstanceAsk> getPendingAsksPastDeadline(final long deadline) {
    final List<MessageStartProcessInstanceAsk> result = new ArrayList<>();
    for (final var askKey : transientState.entriesBefore(deadline)) {
      final var ask = get(askKey.messageKey(), askKey.processDefinitionKey());
      if (ask != null) {
        result.add(ask.copy());
      }
    }
    return result;
  }

  @Override
  public void put(final MessageStartProcessInstanceAsk ask) {
    messageKey.wrapLong(ask.getMessageKey());
    processDefinitionKey.wrapLong(ask.getProcessDefinitionKey());
    columnFamily.upsert(key, ask);
    transientState.add(new PendingAskKey(ask.getMessageKey(), ask.getProcessDefinitionKey()), 0L);
  }

  @Override
  public void remove(final long messageKey, final long processDefinitionKey) {
    this.messageKey.wrapLong(messageKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    columnFamily.deleteIfExists(key);
    transientState.remove(new PendingAskKey(messageKey, processDefinitionKey));
  }

  @Override
  public void backOff(final long messageKey, final long processDefinitionKey) {
    final var ask = get(messageKey, processDefinitionKey);
    if (ask == null) {
      // The ask was already removed (e.g. by a racing success or message expiry); nothing to do.
      return;
    }
    ask.setRejectionCount(Math.min(ask.getRejectionCount() + 1, MAX_REJECTION_COUNT));
    this.messageKey.wrapLong(messageKey);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    columnFamily.upsert(key, ask);
    // Intentionally leave the transient last-sent tracking untouched: the scheduler uses it as the
    // in-flight guard, and resetting it here would make the ask immediately eligible again and
    // defeat the back-off the incremented count is meant to produce.
  }

  @Override
  public void removeAllByMessageKey(final long messageKey) {
    // messageKey is the first component of the composite key, so a prefix scan visits all entries
    // for this message regardless of processDefinitionKey.
    this.messageKey.wrapLong(messageKey);
    final List<Long> processDefinitionKeysToRemove = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        this.messageKey,
        (final DbCompositeKey<DbLong, DbLong> k, final MessageStartProcessInstanceAsk v) ->
            processDefinitionKeysToRemove.add(k.second().getValue()));
    for (final long pdk : processDefinitionKeysToRemove) {
      remove(messageKey, pdk);
    }
  }

  @Override
  public void updateLastSentTime(
      final long messageKey, final long processDefinitionKey, final long lastSentTime) {
    transientState.update(new PendingAskKey(messageKey, processDefinitionKey), lastSentTime);
  }
}
