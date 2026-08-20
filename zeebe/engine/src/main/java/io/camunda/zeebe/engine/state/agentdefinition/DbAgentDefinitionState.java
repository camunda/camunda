/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableAgentDefinitionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.agentdefinition.AgentDefinitionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.LongConsumer;
import org.agrona.DirectBuffer;

public final class DbAgentDefinitionState implements MutableAgentDefinitionState {

  private final DbLong processDefinitionKey = new DbLong();
  private final DbString elementId = new DbString();
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndElementId =
      new DbCompositeKey<>(processDefinitionKey, elementId);
  private final DbLong agentDefinitionKey = new DbLong();
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbLong>
      agentDefinitionKeyColumnFamily;

  private final DbAgentDefinition dbAgentDefinition = new DbAgentDefinition();
  private final ColumnFamily<DbLong, DbAgentDefinition> agentDefinitionColumnFamily;

  public DbAgentDefinitionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    agentDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
            transactionContext,
            processDefinitionKeyAndElementId,
            agentDefinitionKey);
    agentDefinitionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_DEFINITION_BY_KEY,
            transactionContext,
            agentDefinitionKey,
            dbAgentDefinition);
  }

  @Override
  public Long getAgentDefinitionKey(final long processDefinitionKey, final DirectBuffer elementId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.elementId.wrapBuffer(elementId);
    final var stored = agentDefinitionKeyColumnFamily.get(processDefinitionKeyAndElementId);
    return stored == null ? null : stored.getValue();
  }

  @Override
  public AgentDefinitionRecord getAgentDefinition(final long agentDefinitionKey) {
    this.agentDefinitionKey.wrapLong(agentDefinitionKey);
    final var stored = agentDefinitionColumnFamily.get(this.agentDefinitionKey);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public void forEachAgentDefinitionKey(
      final long processDefinitionKey, final LongConsumer callback) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    agentDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          callback.accept(value.getValue());
        });
  }

  @Override
  public void insert(final long agentDefinitionKey, final AgentDefinitionRecord record) {
    processDefinitionKey.wrapLong(record.getProcessDefinitionKey());
    elementId.wrapBuffer(BufferUtil.wrapString(record.getElementId()));
    this.agentDefinitionKey.wrapLong(agentDefinitionKey);
    agentDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndElementId, this.agentDefinitionKey);

    dbAgentDefinition.setRecord(record);
    agentDefinitionColumnFamily.upsert(this.agentDefinitionKey, dbAgentDefinition);
  }

  @Override
  public void delete(final AgentDefinitionRecord record) {
    processDefinitionKey.wrapLong(record.getProcessDefinitionKey());
    elementId.wrapBuffer(BufferUtil.wrapString(record.getElementId()));
    agentDefinitionKeyColumnFamily.deleteIfExists(processDefinitionKeyAndElementId);

    agentDefinitionKey.wrapLong(record.getAgentDefinitionKey());
    agentDefinitionColumnFamily.deleteIfExists(agentDefinitionKey);
  }
}
