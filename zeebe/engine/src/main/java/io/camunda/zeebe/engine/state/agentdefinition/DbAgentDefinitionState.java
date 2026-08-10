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
import org.agrona.DirectBuffer;

public final class DbAgentDefinitionState implements MutableAgentDefinitionState {

  private final DbLong processDefinitionKey = new DbLong();
  private final DbString elementId = new DbString();
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndElementId =
      new DbCompositeKey<>(processDefinitionKey, elementId);
  private final DbLong agentDefinitionKey = new DbLong();
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbLong>
      agentDefinitionKeyColumnFamily;

  public DbAgentDefinitionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    agentDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_DEFINITION_KEY_BY_PROCESS_DEFINITION_KEY_AND_ELEMENT_ID,
            transactionContext,
            processDefinitionKeyAndElementId,
            agentDefinitionKey);
  }

  @Override
  public Long getAgentDefinitionKey(final long processDefinitionKey, final DirectBuffer elementId) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.elementId.wrapBuffer(elementId);
    final var stored = agentDefinitionKeyColumnFamily.get(processDefinitionKeyAndElementId);
    return stored == null ? null : stored.getValue();
  }

  @Override
  public void insert(final long agentDefinitionKey, final AgentDefinitionRecord record) {
    processDefinitionKey.wrapLong(record.getProcessDefinitionKey());
    elementId.wrapBuffer(BufferUtil.wrapString(record.getElementId()));
    this.agentDefinitionKey.wrapLong(agentDefinitionKey);
    agentDefinitionKeyColumnFamily.insert(
        processDefinitionKeyAndElementId, this.agentDefinitionKey);
  }
}
