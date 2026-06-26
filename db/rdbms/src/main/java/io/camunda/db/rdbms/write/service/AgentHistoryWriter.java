/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AgentHistoryMapper;
import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class AgentHistoryWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public AgentHistoryWriter(
      final ExecutionQueue executionQueue,
      final AgentHistoryMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final AgentHistoryDbModel model) {
    model.truncateJobLease(
        vendorDatabaseProperties.userCharColumnSize(),
        vendorDatabaseProperties.charColumnMaxBytes());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_HISTORY,
            WriteStatementType.INSERT,
            model.agentHistoryKey(),
            "io.camunda.db.rdbms.sql.AgentHistoryMapper.insert",
            model));
  }

  public void updateCommitStatus(final AgentHistoryDbModel model) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_HISTORY,
            WriteStatementType.UPDATE,
            model.agentHistoryKey(),
            "io.camunda.db.rdbms.sql.AgentHistoryMapper.updateCommitStatus",
            model));
  }
}
