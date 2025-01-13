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
import java.util.List;

public class AuthorizationWriter {

  private final ExecutionQueue executionQueue;

  public AuthorizationWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void addPermissions(final List<AuthorizationDbModel> authorizations) {
    for (final AuthorizationDbModel authorization : authorizations) {
      final String key = generateKey(authorization);
      if (hasPermissions(authorization)) {
        executionQueue.executeInQueue(
            new QueueItem(
                ContextType.AUTHORIZATION,
                key,
                "io.camunda.db.rdbms.sql.AuthorizationMapper.insert",
                authorization));
      }
    }
  }

  public void removePermissions(final List<AuthorizationDbModel> authorizations) {
    for (final AuthorizationDbModel authorization : authorizations) {
      removePermission(authorization);
    }
  }

  public void removePermission(final AuthorizationDbModel authorization) {
    final String key = generateKey(authorization);
    if (hasPermissions(authorization)) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.AUTHORIZATION,
              key,
              "io.camunda.db.rdbms.sql.AuthorizationMapper.delete",
              authorization));
    }
  }

  private String generateKey(final AuthorizationDbModel authorization) {
    return authorization.ownerKey()
        + "_"
        + authorization.ownerType()
        + "_"
        + authorization.resourceType();
  }

  private static boolean hasPermissions(final AuthorizationDbModel authorization) {
    return authorization.resourceId() != null && !authorization.resourceId().isEmpty();
  }
}
