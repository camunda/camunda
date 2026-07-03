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
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Persists process-instance suspend/resume state (POC #56552).
 *
 * <ul>
 *   <li>{@code SUSPENDED_PROCESS_INSTANCES}: {@code processInstanceKey -> ∅}. Presence ==
 *       suspended.
 *   <li>{@code BUFFERED_PROCESS_INSTANCE_COMMANDS}: {@code (processInstanceKey, seq) ->
 *       BufferedCommand}. The wrapped record carries the original element-command intent via its
 *       internal {@code bufferedElementIntent} field. {@code seq} is the KeyGenerator-issued record
 *       key of the buffering event, which is monotonic per partition and deterministically
 *       recovered on replay, giving replay-deterministic FIFO ordering with no separate counter.
 * </ul>
 */
public final class DbSuspensionState implements MutableSuspensionState {

  private final DbLong processInstanceKey = new DbLong();
  private final ColumnFamily<DbLong, DbNil> suspendedColumnFamily;

  private final DbLong bufferPosition = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> bufferKey =
      new DbCompositeKey<>(processInstanceKey, bufferPosition);
  private final BufferedCommand bufferedCommand = new BufferedCommand();
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, BufferedCommand>
      bufferedCommandColumnFamily;

  public DbSuspensionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    suspendedColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES,
            transactionContext,
            processInstanceKey,
            DbNil.INSTANCE);
    bufferedCommandColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BUFFERED_PROCESS_INSTANCE_COMMANDS,
            transactionContext,
            bufferKey,
            bufferedCommand);
  }

  @Override
  public boolean isSuspended(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    return suspendedColumnFamily.exists(this.processInstanceKey);
  }

  @Override
  public void forEachBufferedCommand(
      final long processInstanceKey, final Consumer<ProcessInstanceRecord> visitor) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    final BiConsumer<DbCompositeKey<DbLong, DbLong>, BufferedCommand> onEntry =
        (key, value) -> visitor.accept(value.getRecord());
    bufferedCommandColumnFamily.whileEqualPrefix(this.processInstanceKey, onEntry);
  }

  @Override
  public void suspend(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    suspendedColumnFamily.upsert(this.processInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void resume(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    suspendedColumnFamily.deleteExisting(this.processInstanceKey);
    clearBuffer(processInstanceKey);
  }

  @Override
  public void bufferCommand(
      final long processInstanceKey, final long position, final ProcessInstanceRecord record) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    bufferPosition.wrapLong(position);
    bufferedCommand.setRecord(record);
    bufferedCommandColumnFamily.upsert(bufferKey, bufferedCommand);
  }

  @Override
  public void clearBuffer(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    // Collect positions first — mutating the column family while iterating it is unsafe.
    final List<Long> positions = new ArrayList<>();
    final BiConsumer<DbCompositeKey<DbLong, DbLong>, BufferedCommand> onEntry =
        (key, value) -> positions.add(key.second().getValue());
    bufferedCommandColumnFamily.whileEqualPrefix(this.processInstanceKey, onEntry);
    for (final Long position : positions) {
      this.processInstanceKey.wrapLong(processInstanceKey);
      bufferPosition.wrapLong(position);
      bufferedCommandColumnFamily.deleteExisting(bufferKey);
    }
  }
}
