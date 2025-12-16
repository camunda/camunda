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

public class RoleWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public RoleWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final RoleDbModel role) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.INSERT,
            role.roleId(),
            "io.camunda.db.rdbms.sql.RoleMapper.insert",
            role));
  }

  public void update(final RoleDbModel role) {
    final boolean wasMerged =
        mergeToQueue(
            role.roleId(),
            b -> b.roleId(role.roleId()).name(role.name()).description(role.description()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.ROLE,
              WriteStatementType.UPDATE,
              role.roleId(),
              "io.camunda.db.rdbms.sql.RoleMapper.update",
              role));
    }
  }

  public void addMember(final RoleMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.INSERT,
            member.roleId(),
            "io.camunda.db.rdbms.sql.RoleMapper.insertMember",
            member));
  }

  public void removeMember(final RoleMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.DELETE,
            member.roleId(),
            "io.camunda.db.rdbms.sql.RoleMapper.deleteMember",
            member));
  }

  public void delete(final String roleId) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.ROLE,
            WriteStatementType.DELETE,
            roleId,
            "io.camunda.db.rdbms.sql.RoleMapper.delete",
            roleId));
  }

  private boolean mergeToQueue(
      final String roleId, final Function<RoleDbModel.Builder, RoleDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.ROLE, roleId, RoleDbModel.class, mergeFunction));
  }
}
