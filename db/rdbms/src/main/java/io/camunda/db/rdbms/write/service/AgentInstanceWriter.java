/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.sql.AgentInstanceMapper.UpsertElementInstanceKeyDto;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.Builder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;
import java.util.function.Function;

public class AgentInstanceWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public AgentInstanceWriter(
      final ExecutionQueue executionQueue, final AgentInstanceMapper mapper) {
    super(mapper);
    this.executionQueue = executionQueue;
  }

  public void create(final AgentInstanceDbModel agentInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AGENT_INSTANCE,
            WriteStatementType.INSERT,
            agentInstance.agentInstanceKey(),
            "io.camunda.db.rdbms.sql.AgentInstanceMapper.insert",
            agentInstance));

    upsertElementInstanceKeys(
        agentInstance.agentInstanceKey(), agentInstance.elementInstanceKeys());
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

    upsertElementInstanceKeys(
        agentInstance.agentInstanceKey(), agentInstance.elementInstanceKeys());
  }

  private void upsertElementInstanceKeys(
      final long agentInstanceKey, final List<Long> elementInstanceKeys) {
    if (elementInstanceKeys == null || elementInstanceKeys.isEmpty()) {
      return;
    }
    for (final long elementInstanceKey : elementInstanceKeys) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AGENT_INSTANCE,
              WriteStatementType.INSERT,
              agentInstanceKey,
              "io.camunda.db.rdbms.sql.AgentInstanceMapper.upsertElementInstanceKey",
              new UpsertElementInstanceKeyDto(agentInstanceKey, elementInstanceKey)));
    }
  }

  private boolean mergeToQueue(final long key, final Function<Builder, Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(
            ContextType.AGENT_INSTANCE, key, AgentInstanceDbModel.class, mergeFunction));
  }
}
