/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentinstance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;

public final class DbAgentInstanceState implements MutableAgentInstanceState {

  private final DbLong agentInstanceKey = new DbLong();
  private final DbAgentInstance dbAgentInstance = new DbAgentInstance();
  private final ColumnFamily<DbLong, DbAgentInstance> agentInstancesColumnFamily;

  public DbAgentInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    agentInstancesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_INSTANCES,
            transactionContext,
            agentInstanceKey,
            dbAgentInstance);
  }

  @Override
  public AgentInstanceRecord getRecord(final long key) {
    agentInstanceKey.wrapLong(key);
    final var stored = agentInstancesColumnFamily.get(agentInstanceKey, DbAgentInstance::new);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public void insert(final long key, final AgentInstanceRecord record) {
    agentInstanceKey.wrapLong(key);
    dbAgentInstance.setRecord(record);
    agentInstancesColumnFamily.insert(agentInstanceKey, dbAgentInstance);
  }
}
