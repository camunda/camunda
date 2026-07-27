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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableAgentInstanceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.util.ArrayList;
import java.util.List;

public final class DbAgentInstanceState implements MutableAgentInstanceState {

  private final DbLong agentInstanceKey = new DbLong();
  private final DbAgentInstance dbAgentInstance = new DbAgentInstance();
  private final ColumnFamily<DbLong, DbAgentInstance> agentInstancesColumnFamily;

  // Secondary index: (processInstanceKey, agentInstanceKey) -> nil
  private final DbLong processInstanceKey = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyAndAgentInstanceKey =
      new DbCompositeKey<>(processInstanceKey, agentInstanceKey);
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      byProcessInstanceKeyColumnFamily;

  public DbAgentInstanceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    agentInstancesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_INSTANCES,
            transactionContext,
            agentInstanceKey,
            dbAgentInstance);
    byProcessInstanceKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AGENT_INSTANCES_BY_PROCESS_INSTANCE_KEY,
            transactionContext,
            processInstanceKeyAndAgentInstanceKey,
            DbNil.INSTANCE);
  }

  @Override
  public AgentInstanceRecord getRecord(final long key) {
    agentInstanceKey.wrapLong(key);
    final var stored = agentInstancesColumnFamily.get(agentInstanceKey, DbAgentInstance::new);
    return stored == null ? null : stored.getRecord();
  }

  @Override
  public List<Long> getAgentInstanceKeysByProcessInstanceKey(final long piKey) {
    final List<Long> keys = new ArrayList<>();
    processInstanceKey.wrapLong(piKey);
    byProcessInstanceKeyColumnFamily.whileEqualPrefix(
        processInstanceKey,
        key -> {
          keys.add(key.second().getValue());
        });
    return keys;
  }

  @Override
  public void insert(final long key, final AgentInstanceRecord record) {
    agentInstanceKey.wrapLong(key);
    dbAgentInstance.setRecord(record);
    agentInstancesColumnFamily.insert(agentInstanceKey, dbAgentInstance);
    processInstanceKey.wrapLong(record.getProcessInstanceKey());
    byProcessInstanceKeyColumnFamily.insert(processInstanceKeyAndAgentInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void update(final long key, final AgentInstanceRecord record) {
    agentInstanceKey.wrapLong(key);
    dbAgentInstance.setRecord(record);
    agentInstancesColumnFamily.update(agentInstanceKey, dbAgentInstance);
  }

  @Override
  public void delete(final long key) {
    final var existing = getRecord(key);
    if (existing == null) {
      return;
    }
    delete(key, existing);
  }

  @Override
  public void delete(final long key, final AgentInstanceRecord record) {
    agentInstanceKey.wrapLong(key);
    processInstanceKey.wrapLong(record.getProcessInstanceKey());
    agentInstancesColumnFamily.deleteIfExists(agentInstanceKey);
    byProcessInstanceKeyColumnFamily.deleteIfExists(processInstanceKeyAndAgentInstanceKey);
  }
}
