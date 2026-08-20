/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class RoleWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final RoleWriter writer = new RoleWriter(executionQueue);

  @Test
  void shouldCreateRole() {
    final var model =
        new RoleDbModel.Builder()
            .roleId("role1")
            .name("Test Role")
            .description("Description")
            .build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.ROLE,
                    WriteStatementType.INSERT,
                    model.roleId(),
                    "io.camunda.db.rdbms.sql.RoleMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateRoleWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    final var model = new RoleDbModel.Builder().roleId("role1").name("Updated Name").build();

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.ROLE,
                    WriteStatementType.UPDATE,
                    model.roleId(),
                    "io.camunda.db.rdbms.sql.RoleMapper.update",
                    model)));
  }

  @Test
  void shouldAddMember() {
    final var member = new RoleMemberDbModel("role1", "user1", "USER");

    writer.addMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.ROLE,
                    WriteStatementType.INSERT,
                    member.roleId(),
                    "io.camunda.db.rdbms.sql.RoleMapper.insertMember",
                    member)));
  }

  @Test
  void shouldRemoveMember() {
    final var member = new RoleMemberDbModel("role1", "user1", "USER");

    writer.removeMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.ROLE,
                    WriteStatementType.DELETE,
                    member.roleId(),
                    "io.camunda.db.rdbms.sql.RoleMapper.deleteMember",
                    member)));
  }

  @Test
  void shouldDeleteRole() {
    final String roleId = "role1";

    writer.delete(roleId);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.ROLE,
                    WriteStatementType.DELETE,
                    roleId,
                    "io.camunda.db.rdbms.sql.RoleMapper.delete",
                    roleId)));
  }
}
