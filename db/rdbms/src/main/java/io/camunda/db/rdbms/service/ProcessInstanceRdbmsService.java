/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ProcessInstanceDbFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceRdbmsService {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceRdbmsService.class);

  private final ExecutionQueue executionQueue;
  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceRdbmsService(
      final ExecutionQueue executionQueue, final ProcessInstanceMapper processInstanceMapper) {
    this.executionQueue = executionQueue;
    this.processInstanceMapper = processInstanceMapper;
  }

  public void create(final ProcessInstanceDbModel processInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            processInstance.processInstanceKey(),
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insert",
            processInstance));
  }

  public void update(final ProcessInstanceDbModel processInstance) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            processInstance.processInstanceKey(),
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.update",
            processInstance));
  }

  public void updateCurrentElementId(final long processInstanceKey, final String elementId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateCurrentElementId",
            Map.of("processInstanceKey", processInstanceKey, "elementId", elementId)));
  }

  public ProcessInstanceEntity findOne(final Long processInstanceKey) {
    LOG.trace("[RDBMS DB] Search for process instance with key {}", processInstanceKey);
    return processInstanceMapper.findOne(processInstanceKey);
  }

  public SearchResult search(ProcessInstanceDbFilter filter) {
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", filter);
    final var totalHits = processInstanceMapper.count(filter);
    final var hits = processInstanceMapper.search(filter);
    return new SearchResult(hits, totalHits);
  }

  public record SearchResult(List<ProcessInstanceEntity> hits, Integer total) {}
}
