/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ProcessDefinitionModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionRdbmsService {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionRdbmsService.class);

  private final ExecutionQueue executionQueue;
  private final ProcessDefinitionMapper processDefinitionMapper;

  private final HashMap<Pair<Long, Long>, ProcessDefinitionModel> CACHE = new HashMap<>();

  public ProcessDefinitionRdbmsService(final ExecutionQueue executionQueue, final ProcessDefinitionMapper processDefinitionMapper) {
    this.executionQueue = executionQueue;
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public void save(final ProcessDefinitionModel processDefinition) {
    executionQueue.executeInQueue(new QueueItem(
        ContextType.PROCESS_INSTANCE,
        processDefinition.processDefinitionKey(),
        "io.camunda.db.rdbms.sql.ProcessDefinitionMapper.insert",
        processDefinition
    ));
  }

  public Optional<ProcessDefinitionModel> findOne(final Long processDefinitionKey, long version) {
    if (!CACHE.containsKey(Pair.of(processDefinitionKey, version))) {
      var result = processDefinitionMapper.findOne(Map.of("processDefinitionKey", processDefinitionKey, "version", version));

      if (result != null) {
        CACHE.put(Pair.of(processDefinitionKey, version), result);
        return Optional.of(result);
      }
    }

    return Optional.empty();
  }

}
