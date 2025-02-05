/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;

public class AuthorizationWriter {

  private final ExecutionQueue executionQueue;

  public AuthorizationWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void createAuthorization(final AuthorizationDbModel authorization) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AUTHORIZATION,
            authorization.authorizationKey().toString(),
            "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
            authorization));
  }

  public void deleteAuthorization(final AuthorizationDbModel authorization) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AUTHORIZATION,
            authorization.authorizationKey().toString(),
            "io.camunda.db.rdbms.sql.AuthorizationMapper.delete",
            authorization));
  }
}
