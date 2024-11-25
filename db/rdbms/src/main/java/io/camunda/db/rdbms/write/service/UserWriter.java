/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import java.util.function.Function;

public class UserWriter {

  private final ExecutionQueue executionQueue;

  public UserWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final UserDbModel user) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER, user.userKey(), "io.camunda.db.rdbms.sql.UserMapper.insert", user));
  }

  public void update(final UserDbModel user) {
    final boolean wasMerged =
        mergeToQueue(
            user.userKey(), b -> b.username(user.username()).name(user.name()).email(user.email()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.USER, user.userKey(), "io.camunda.db.rdbms.sql.UserMapper.update", user));
    }
  }

  private boolean mergeToQueue(
      final long key, final Function<UserDbModel.Builder, UserDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.USER, key, UserDbModel.class, mergeFunction));
  }
}
