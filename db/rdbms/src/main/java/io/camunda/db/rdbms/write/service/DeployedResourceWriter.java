/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.DeployedResourceDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class DeployedResourceWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public DeployedResourceWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final DeployedResourceDbModel resource) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DEPLOYED_RESOURCE,
            WriteStatementType.INSERT,
            resource.resourceKey(),
            "io.camunda.db.rdbms.sql.DeployedResourceMapper.insert",
            resource));
  }

  public void delete(final Long resourceKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.DEPLOYED_RESOURCE,
            WriteStatementType.DELETE,
            resourceKey,
            "io.camunda.db.rdbms.sql.DeployedResourceMapper.delete",
            resourceKey));
  }
}
