/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.EndFlowNodeDto;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.UpdateIncidentDto;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.InsertFlowNodeInstanceMerger;
import io.camunda.db.rdbms.write.queue.ListParameterUpsertMerger;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class FlowNodeInstanceWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final RdbmsWriterConfig config;

  public FlowNodeInstanceWriter(
      final ExecutionQueue executionQueue,
      final FlowNodeInstanceMapper mapper,
      final RdbmsWriterConfig config) {
    super(mapper);
    this.executionQueue = executionQueue;
    this.config = config;
  }

  public void create(final FlowNodeInstanceDbModel flowNode) {
    final var wasMerged =
        executionQueue.tryMergeWithExistingQueueItem(
            new InsertFlowNodeInstanceMerger(
                flowNode, config.insertBatchingConfig().flowNodeInsertBatchSize()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              WriteStatementType.INSERT,
              flowNode.flowNodeInstanceKey(),
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.insert",
              new BatchInsertDto<>(flowNode)));
    }
  }

  public void update(final FlowNodeInstanceDbModel flowNode) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.FLOW_NODE,
            WriteStatementType.UPDATE,
            flowNode.flowNodeInstanceKey(),
            "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.update",
            flowNode));
  }

  public void finish(final long key, final FlowNodeState state, final OffsetDateTime endDate) {
    final boolean wasMerged = mergeToQueue(key, b -> b.state(state).endDate(endDate));

    if (!wasMerged) {
      final var dto = new EndFlowNodeDto(key, state, endDate);
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              WriteStatementType.UPDATE,
              key,
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate",
              dto));
    }
  }

  public void createIncident(final long flowNodeInstanceKey, final long incidentKey) {
    updateIncident(flowNodeInstanceKey, incidentKey);
  }

  public void resolveIncident(final long flowNodeInstanceKey) {
    updateIncident(flowNodeInstanceKey, null);
  }

  public void createSubprocessIncident(final long flowNodeInstanceKey) {
    final boolean wasMerged =
        mergeToQueue(
            flowNodeInstanceKey, b -> b.numSubprocessIncidents(b.numSubprocessIncidents() + 1));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              WriteStatementType.UPDATE,
              flowNodeInstanceKey,
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.incrementSubprocessIncidentCount",
              flowNodeInstanceKey));
    }
  }

  public void resolveSubprocessIncident(final long flowNodeInstanceKey) {
    final boolean wasMerged =
        mergeToQueue(
            flowNodeInstanceKey, b -> b.numSubprocessIncidents(b.numSubprocessIncidents() - 1));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              WriteStatementType.UPDATE,
              flowNodeInstanceKey,
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.decrementSubprocessIncidentCount",
              flowNodeInstanceKey));
    }
  }

  private void updateIncident(final long flowNodeInstanceKey, final Long incidentKey) {
    final boolean wasMerged = mergeToQueue(flowNodeInstanceKey, b -> b.incidentKey(incidentKey));

    if (!wasMerged) {
      final var dto = new UpdateIncidentDto(flowNodeInstanceKey, incidentKey);
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.FLOW_NODE,
              WriteStatementType.UPDATE,
              flowNodeInstanceKey,
              "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateIncident",
              dto));
    }
  }

  private boolean mergeToQueue(
      final long key,
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new ListParameterUpsertMerger<>(
            ContextType.FLOW_NODE,
            key,
            FlowNodeInstanceDbModel::flowNodeInstanceKey,
            mergeFunction));
  }
}
