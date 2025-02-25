/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.Function;

public class RoleWriter {

  private final ExecutionQueue executionQueue;

  public RoleWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final RoleDbModel role) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.INSERT,
            role.roleKey(),
            "io.camunda.db.rdbms.sql.RoleMapper.insert",
            role));
  }

  public void update(final RoleDbModel role) {
    final boolean wasMerged = mergeToQueue(role.roleKey(), b -> b.name(role.name()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.ROLE,
              WriteStatementType.UPDATE,
              role.roleKey(),
              "io.camunda.db.rdbms.sql.RoleMapper.update",
              role));
    }
  }

  public void addMember(final RoleMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.INSERT,
            member.roleKey(),
            "io.camunda.db.rdbms.sql.RoleMapper.insertMember",
            member));
  }

  public void removeMember(final RoleMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.DELETE,
            member.roleKey(),
            "io.camunda.db.rdbms.sql.RoleMapper.deleteMember",
            member));
  }

  public void delete(final long roleKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.DELETE,
            roleKey,
            "io.camunda.db.rdbms.sql.RoleMapper.delete",
            roleKey));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.DELETE,
            roleKey,
            "io.camunda.db.rdbms.sql.RoleMapper.deleteAllMembers",
            roleKey));
  }

  private boolean mergeToQueue(
      final long key, final Function<RoleDbModel.Builder, RoleDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.ROLE, key, RoleDbModel.class, mergeFunction));
  }
}
