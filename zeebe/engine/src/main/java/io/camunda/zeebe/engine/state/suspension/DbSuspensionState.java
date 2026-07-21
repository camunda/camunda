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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBufferedCommandRecord;
import java.util.ArrayList;
import java.util.List;

public final class DbSuspensionState implements MutableSuspensionState {

  private final DbLong processInstanceKey = new DbLong();
  private final SuspensionMarkerValue suspensionMarkerValue = new SuspensionMarkerValue();
  private final ColumnFamily<DbLong, SuspensionMarkerValue> suspensionColumnFamily;

  private final DbLong bufferedCommandKey = new DbLong();
  private final DbProcessInstanceBufferedCommand dbBufferedCommand =
      new DbProcessInstanceBufferedCommand();
  private final ColumnFamily<DbLong, DbProcessInstanceBufferedCommand> bufferedCommandColumnFamily;

  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyAndBufferedCommandKey =
      new DbCompositeKey<>(processInstanceKey, bufferedCommandKey);
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      bufferedCommandByProcessInstanceKeyColumnFamily;

  public DbSuspensionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    suspensionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES,
            transactionContext,
            processInstanceKey,
            suspensionMarkerValue);
    bufferedCommandColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BUFFERED_PROCESS_INSTANCE_COMMANDS,
            transactionContext,
            bufferedCommandKey,
            dbBufferedCommand);
    bufferedCommandByProcessInstanceKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BUFFERED_PROCESS_INSTANCE_COMMANDS_BY_PROCESS_INSTANCE_KEY,
            transactionContext,
            processInstanceKeyAndBufferedCommandKey,
            DbNil.INSTANCE);
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
        (compositeKey, nil) -> {
          final long bufferedKey = compositeKey.second().getValue();
          bufferedCommandKey.wrapLong(bufferedKey);
          final var stored =
              bufferedCommandColumnFamily.get(
                  bufferedCommandKey, DbProcessInstanceBufferedCommand::new);
          if (stored != null) {
            visitor.visit(bufferedKey, stored.getRecord());
          }
        });
  }

  @Override
  public void bufferCommand(
      final long bufferedCommandKeyValue, final ProcessInstanceBufferedCommandRecord command) {
    processInstanceKey.wrapLong(command.getProcessInstanceKey());
    bufferedCommandKey.wrapLong(bufferedCommandKeyValue);
    dbBufferedCommand.setRecord(command);
    bufferedCommandColumnFamily.insert(bufferedCommandKey, dbBufferedCommand);
    bufferedCommandByProcessInstanceKeyColumnFamily.insert(
        processInstanceKeyAndBufferedCommandKey, DbNil.INSTANCE);
  }

  @Override
  public void removeBufferedCommand(final long bufferedCommandKeyValue) {
    bufferedCommandKey.wrapLong(bufferedCommandKeyValue);
    final var stored = bufferedCommandColumnFamily.get(bufferedCommandKey);
    if (stored == null) {
      // no-op: nothing buffered under this key (already removed, or never existed) — the normal
      // case per the interface contract. Primary and secondary index are always written together
      // in the same transaction (see #bufferCommand), so this also means there is no secondary
      // entry to clean up.
      return;
    }
    // derive the secondary-index key from the stored record itself, mirroring #bufferCommand,
    // rather than taking it as a separate parameter that could desync from it
    processInstanceKey.wrapLong(stored.getRecord().getProcessInstanceKey());
    bufferedCommandColumnFamily.deleteIfExists(bufferedCommandKey);
    bufferedCommandByProcessInstanceKeyColumnFamily.deleteIfExists(
        processInstanceKeyAndBufferedCommandKey);
  }

  @Override
  public void clearBufferedCommands(final long processInstanceKeyValue) {
    processInstanceKey.wrapLong(processInstanceKeyValue);
    final List<Long> keysToRemove = new ArrayList<>();
    bufferedCommandByProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        (compositeKey, nil) -> {
          keysToRemove.add(compositeKey.second().getValue());
        });
    // delete directly instead of delegating to #removeBufferedCommand: the prefix just iterated
    // is already the authoritative processInstanceKey for every one of these entries, so there is
    // no need to re-fetch each record just to re-derive it
    keysToRemove.forEach(
        k -> {
          bufferedCommandKey.wrapLong(k);
          bufferedCommandColumnFamily.deleteIfExists(bufferedCommandKey);
          bufferedCommandByProcessInstanceKeyColumnFamily.deleteIfExists(
              processInstanceKeyAndBufferedCommandKey);
        });
  }
}
