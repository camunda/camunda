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
import java.util.concurrent.atomic.AtomicInteger;

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
        (compositeKey, nil) -> {
          count.incrementAndGet();
        });
    return count.get();
  }

  @Override
  public Optional<BufferedCommand> getOldestBufferedCommand(final long key) {
    processInstanceKey.wrapLong(key);
    final var oldest = new BufferedCommand[1];
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        (compositeKey, stored) -> {
          // the key is ordered by bufferedCommandKey, so the first hit is the oldest. stored is
          // the column family's single reusable value instance - copy it, since the caller (the
          // resume/drain hot path) holds onto this well beyond this method returning, and any
          // later read from this column family would otherwise silently mutate it out from under
          // them
          final var copy = new BufferedCommandRecord();
          copy.copyFrom(stored.getRecord());
          oldest[0] = new BufferedCommand(compositeKey.second().getValue(), copy);
          return false;
        });
    return Optional.ofNullable(oldest[0]);
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
        (compositeKey, stored) -> {
          keysToRemove.add(compositeKey.second().getValue());
        });
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
