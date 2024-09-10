/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.service;

import io.camunda.db.rdbms.domain.ProcessInstanceFilter;
import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.db.rdbms.queue.ContextType;
import io.camunda.db.rdbms.queue.ExecutionQueue;
import io.camunda.db.rdbms.queue.QueueItem;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import java.util.List;

public class ProcessInstanceRdbmsService {

  private final ExecutionQueue executionQueue;
  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceRdbmsService(final ExecutionQueue executionQueue, final ProcessInstanceMapper processInstanceMapper) {
    this.executionQueue = executionQueue;
    this.processInstanceMapper = processInstanceMapper;
  }

  public void create(final ProcessInstanceModel processInstance) {
    executionQueue.executeInQueue(new QueueItem(
        ContextType.PROCESS_INSTANCE,
        processInstance.processInstanceKey(),
        "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insert",
        processInstance
    ));
  }

  public void update(final ProcessInstanceModel processInstance) {
    executionQueue.executeInQueue(new QueueItem(
        ContextType.PROCESS_INSTANCE,
        processInstance.processInstanceKey(),
        "io.camunda.db.rdbms.sql.ProcessInstanceMapper.update",
        processInstance
    ));
  }

  public ProcessInstanceModel findOne(final Long processInstanceKey) {
    return processInstanceMapper.findOne(processInstanceKey);
  }

  public List<ProcessInstanceModel> search(ProcessInstanceFilter filter) {
    return processInstanceMapper.search(filter);
  }

}
