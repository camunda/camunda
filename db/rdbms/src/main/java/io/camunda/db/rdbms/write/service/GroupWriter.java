/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.Function;

public class GroupWriter {

  private final ExecutionQueue executionQueue;

  public GroupWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final GroupDbModel group) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GROUP,
            WriteStatementType.INSERT,
            group.groupKey(),
            "io.camunda.db.rdbms.sql.GroupMapper.insert",
            group));
  }

  public void update(final GroupDbModel group) {
    final boolean wasMerged = mergeToQueue(group.groupKey(), b -> b.name(group.name()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.GROUP,
              WriteStatementType.UPDATE,
              group.groupKey(),
              "io.camunda.db.rdbms.sql.GroupMapper.update",
              group));
    }
  }

  public void addMember(final GroupMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GROUP,
            WriteStatementType.INSERT,
            member.groupKey(),
            "io.camunda.db.rdbms.sql.GroupMapper.insertMember",
            member));
  }

  public void removeMember(final GroupMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GROUP,
            WriteStatementType.DELETE,
            member.groupKey(),
            "io.camunda.db.rdbms.sql.GroupMapper.deleteMember",
            member));
  }

  public void delete(final long groupKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GROUP,
            WriteStatementType.DELETE,
            groupKey,
            "io.camunda.db.rdbms.sql.GroupMapper.delete",
            groupKey));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.GROUP,
            WriteStatementType.DELETE,
            groupKey,
            "io.camunda.db.rdbms.sql.GroupMapper.deleteAllMembers",
            groupKey));
  }

  private boolean mergeToQueue(
      final long key, final Function<GroupDbModel.Builder, GroupDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.GROUP, key, GroupDbModel.class, mergeFunction));
  }
}
