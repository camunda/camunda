/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.managedscriptdefinition;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableManagedScriptDefinitionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

public final class DbManagedScriptDefinitionState implements MutableManagedScriptDefinitionState {

  private final DbLong processDefinitionKey = new DbLong();
  private final DbString elementId = new DbString();
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndElementId =
      new DbCompositeKey<>(processDefinitionKey, elementId);
  private final DbLong managedScriptDefinitionKey = new DbLong();
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbLong>
      managedScriptDefinitionKeyColumnFamily;

  private final DbManagedScriptDefinition dbManagedScriptDefinition =
      new DbManagedScriptDefinition();
  private final ColumnFamily<DbLong, DbManagedScriptDefinition> managedScriptDefinitionColumnFamily;

  public DbManagedScriptDefinitionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    managedScriptDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MANAGED_SCRIPT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
            transactionContext,
            processDefinitionKeyAndElementId,
            managedScriptDefinitionKey);
    managedScriptDefinitionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MANAGED_SCRIPT_DEFINITION_BY_KEY,
            transactionContext,
            managedScriptDefinitionKey,
            dbManagedScriptDefinition);
  }

  @Override
  public Long getManagedScriptDefinitionKey(
      final long processDefinitionKey, final DirectBuffer elementId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.elementId.wrapBuffer(elementId);
    final var stored = managedScriptDefinitionKeyColumnFamily.get(processDefinitionKeyAndElementId);
    return stored == null ? null : stored.getValue();
  }

  @Override
  public ManagedScriptDefinitionRecord getManagedScriptDefinition(
      final long managedScriptDefinitionKey) {
    this.managedScriptDefinitionKey.wrapLong(managedScriptDefinitionKey);
    final var stored = managedScriptDefinitionColumnFamily.get(this.managedScriptDefinitionKey);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public void forEachManagedScriptDefinitionKey(final LongConsumer callback) {
    managedScriptDefinitionColumnFamily.forEach((key, value) -> callback.accept(key.getValue()));
  }

  @Override
  public void forEachManagedScriptDefinitionKey(
      final long processDefinitionKey, final LongConsumer callback) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    managedScriptDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          callback.accept(value.getValue());
        });
  }

  @Override
  public void insert(
      final long managedScriptDefinitionKey, final ManagedScriptDefinitionRecord record) {
    processDefinitionKey.wrapLong(record.getProcessDefinitionKey());
    elementId.wrapBuffer(BufferUtil.wrapString(record.getElementId()));
    this.managedScriptDefinitionKey.wrapLong(managedScriptDefinitionKey);
    managedScriptDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndElementId, this.managedScriptDefinitionKey);

    dbManagedScriptDefinition.setRecord(record);
    managedScriptDefinitionColumnFamily.upsert(
        this.managedScriptDefinitionKey, dbManagedScriptDefinition);
  }

  @Override
  public void delete(final ManagedScriptDefinitionRecord record) {
    processDefinitionKey.wrapLong(record.getProcessDefinitionKey());
    elementId.wrapBuffer(BufferUtil.wrapString(record.getElementId()));
    managedScriptDefinitionKeyColumnFamily.deleteIfExists(processDefinitionKeyAndElementId);

    managedScriptDefinitionKey.wrapLong(record.getManagedScriptDefinitionKey());
    managedScriptDefinitionColumnFamily.deleteIfExists(managedScriptDefinitionKey);
  }
}
