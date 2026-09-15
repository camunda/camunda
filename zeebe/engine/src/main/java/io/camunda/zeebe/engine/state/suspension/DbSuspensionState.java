/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DbSuspensionState implements MutableSuspensionState {

  private final DbLong processInstanceKey = new DbLong();
  private final SuspensionMarkerValue suspensionMarkerValue = new SuspensionMarkerValue();
  private final ColumnFamily<DbLong, SuspensionMarkerValue> suspensionColumnFamily;

  private final DbLong bufferedCommandKey = new DbLong();
  private final DbBufferedCommand dbBufferedCommand = new DbBufferedCommand();

  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyAndBufferedCommandKey =
      new DbCompositeKey<>(processInstanceKey, bufferedCommandKey);
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbBufferedCommand>
      bufferedCommandByProcessInstanceKeyColumnFamily;

  public DbSuspensionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    suspensionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES,
            transactionContext,
            processInstanceKey,
            suspensionMarkerValue);
    bufferedCommandByProcessInstanceKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BUFFERED_PROCESS_INSTANCE_COMMANDS_BY_PROCESS_INSTANCE_KEY,
            transactionContext,
            processInstanceKeyAndBufferedCommandKey,
            dbBufferedCommand);
  }

  @Override
  public SuspensionState.State getSuspensionState(final long key) {
    processInstanceKey.wrapLong(key);
    final var stored = suspensionColumnFamily.get(processInstanceKey);
    return stored == null ? null : stored.getState();
  }

  @Override
  public boolean isSuspended(final long key) {
    processInstanceKey.wrapLong(key);
    return suspensionColumnFamily.exists(processInstanceKey);
  }

  @Override
  public void setSuspensionState(final long key, final SuspensionState.State state) {
    processInstanceKey.wrapLong(key);
    suspensionMarkerValue.setState(state);
    suspensionColumnFamily.upsert(processInstanceKey, suspensionMarkerValue);
  }

  @Override
  public void removeSuspensionState(final long key) {
    processInstanceKey.wrapLong(key);
    suspensionColumnFamily.deleteIfExists(processInstanceKey);
  }

  @Override
  public void visitBufferedCommands(final long key, final BufferedCommandVisitor visitor) {
    processInstanceKey.wrapLong(key);
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        (compositeKey, stored) -> {
          final long bufferedKey = compositeKey.second().getValue();
          // stored is the column family's single reusable value instance, re-wrapped for every
          // row - copy it so a visitor collecting values across multiple rows doesn't end up with
          // every entry aliasing the same (by-then-overwritten) buffer
          final var copy = new BufferedCommandRecord();
          copy.copyFrom(stored.getRecord());
          visitor.visit(bufferedKey, copy);
        });
  }

  @Override
  public int countBufferedCommands(final long key) {
    processInstanceKey.wrapLong(key);
    final var count = new AtomicInteger();
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        (Consumer<DbCompositeKey<DbLong, DbLong>>) compositeKey -> count.incrementAndGet());
    return count.get();
  }

  @Override
  public DrainLookup findNextBufferedCommand(final long key, final long afterCommandKey) {
    processInstanceKey.wrapLong(key);
    final long startAtBufferedKey = afterCommandKey < 0 ? 0 : afterCommandKey;
    bufferedCommandKey.wrapLong(startAtBufferedKey);

    final var oldest = new AtomicReference<BufferedCommand>();
    final var hasMore = new AtomicBoolean();
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        processInstanceKeyAndBufferedCommandKey,
        (compositeKey, stored) -> {
          if (compositeKey.second().getValue() == startAtBufferedKey) {
            return true; // skip the entry at afterCommandKey itself
          }
          if (oldest.get() == null) {
            final var copy = new BufferedCommandRecord();
            copy.copyFrom(stored.getRecord()); // stored is reused per row - copy before it changes
            oldest.set(new BufferedCommand(compositeKey.second().getValue(), copy));
            return true; // peek one more row to see if anything follows
          }
          hasMore.set(true);
          return false;
        });
    return new DrainLookup(Optional.ofNullable(oldest.get()), hasMore.get());
  }

  @Override
  public void bufferCommand(
      final long bufferedCommandKeyValue, final BufferedCommandRecord command) {
    processInstanceKey.wrapLong(command.getProcessInstanceKey());
    bufferedCommandKey.wrapLong(bufferedCommandKeyValue);
    dbBufferedCommand.setRecord(command);
    bufferedCommandByProcessInstanceKeyColumnFamily.insert(
        processInstanceKeyAndBufferedCommandKey, dbBufferedCommand);
  }

  @Override
  public void removeBufferedCommand(
      final long processInstanceKeyValue, final long bufferedCommandKeyValue) {
    processInstanceKey.wrapLong(processInstanceKeyValue);
    bufferedCommandKey.wrapLong(bufferedCommandKeyValue);
    bufferedCommandByProcessInstanceKeyColumnFamily.deleteIfExists(
        processInstanceKeyAndBufferedCommandKey);
  }

  @Override
  public void clearBufferedCommands(final long processInstanceKeyValue) {
    processInstanceKey.wrapLong(processInstanceKeyValue);
    final List<Long> keysToRemove = new ArrayList<>();
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        (Consumer<DbCompositeKey<DbLong, DbLong>>)
            compositeKey -> keysToRemove.add(compositeKey.second().getValue()));
    // processInstanceKey is already wrapped to the prefix just iterated above; only the buffered
    // command key needs to change per entry
    keysToRemove.forEach(
        k -> {
          bufferedCommandKey.wrapLong(k);
          bufferedCommandByProcessInstanceKeyColumnFamily.deleteIfExists(
              processInstanceKeyAndBufferedCommandKey);
        });
  }
}
