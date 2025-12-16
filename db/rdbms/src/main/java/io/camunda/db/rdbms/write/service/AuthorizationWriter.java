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
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class AuthorizationWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public AuthorizationWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void createAuthorization(final AuthorizationDbModel authorization) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AUTHORIZATION,
            WriteStatementType.INSERT,
            authorization.authorizationKey().toString(),
            "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
            authorization));
  }

  public void updateAuthorization(final AuthorizationDbModel authorization) {
    // It's easiest to just recreate the authorization instead of creating a complex query to
    // determine a changeset and act accordingly.
    deleteAuthorization(authorization);
    createAuthorization(authorization);
  }

  public void deleteAuthorization(final AuthorizationDbModel authorization) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.AUTHORIZATION,
            WriteStatementType.DELETE,
            authorization.authorizationKey().toString(),
            "io.camunda.db.rdbms.sql.AuthorizationMapper.delete",
            authorization));
  }
}
