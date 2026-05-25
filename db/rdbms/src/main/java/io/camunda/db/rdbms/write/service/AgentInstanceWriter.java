/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.Builder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.Function;

public class AgentInstanceWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public AgentInstanceWriter(
      final ExecutionQueue executionQueue,
      final AgentInstanceMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final AgentInstanceDbModel agentInstance) {
    agentInstance.truncateDefinitionFields(
        vendorDatabaseProperties.userCharColumnSize(),
        vendorDatabaseProperties.charColumnMaxBytes());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_INSTANCE,
            WriteStatementType.INSERT,
            agentInstance.agentInstanceKey(),
            "io.camunda.db.rdbms.sql.AgentInstanceMapper.insert",
            agentInstance));

    insertElementInstanceKeys(agentInstance);
  }

  public void update(final AgentInstanceDbModel agentInstance) {
    final boolean wasMerged =
        mergeToQueue(
            agentInstance.agentInstanceKey(),
            b -> {
              b.status(agentInstance.status())
                  .inputTokens(agentInstance.inputTokens())
                  .outputTokens(agentInstance.outputTokens())
                  .modelCalls(agentInstance.modelCalls())
                  .toolCalls(agentInstance.toolCalls())
                  .toolValues(agentInstance.toolValues())
                  .lastUpdatedDate(agentInstance.lastUpdatedDate());
              if (agentInstance.completionDate() != null) {
                b.completionDate(agentInstance.completionDate());
              }
              return b;
            });

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AGENT_INSTANCE,
              WriteStatementType.UPDATE,
              agentInstance.agentInstanceKey(),
              "io.camunda.db.rdbms.sql.AgentInstanceMapper.update",
              agentInstance));
    }

    syncElementInstanceKeys(agentInstance);
  }

  /**
   * Inserts element instance keys without a prior DELETE. Safe to call on the create path where no
   * child rows can pre-exist (the engine guarantees a CREATED event is emitted at most once per
   * agent instance key).
   */
  private void insertElementInstanceKeys(final AgentInstanceDbModel agentInstance) {
    if (agentInstance.elementInstanceKeys() != null
        && !agentInstance.elementInstanceKeys().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AGENT_INSTANCE,
              WriteStatementType.INSERT,
              agentInstance.agentInstanceKey(),
              "io.camunda.db.rdbms.sql.AgentInstanceMapper.insertElementInstanceKeys",
              agentInstance));
    }
  }

  /**
   * Replaces the full set of element instance keys: DELETE existing rows then INSERT the current
   * set. Required on the update path where the list may have changed since the last write.
   */
  private void syncElementInstanceKeys(final AgentInstanceDbModel agentInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_INSTANCE,
            WriteStatementType.DELETE,
            agentInstance.agentInstanceKey(),
            "io.camunda.db.rdbms.sql.AgentInstanceMapper.deleteElementInstanceKeys",
            agentInstance.agentInstanceKey()));

    insertElementInstanceKeys(agentInstance);
  }

  private boolean mergeToQueue(final long key, final Function<Builder, Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(
            ContextType.AGENT_INSTANCE, key, AgentInstanceDbModel.class, mergeFunction));
  }
}
