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

import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class GroupWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final GroupWriter writer = new GroupWriter(executionQueue);

  @Test
  void shouldCreateGroup() {
    final var model =
        new GroupDbModel.Builder()
            .groupId("group1")
            .name("Test Group")
            .description("Description")
            .build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GROUP,
                    WriteStatementType.INSERT,
                    model.groupId(),
                    "io.camunda.db.rdbms.sql.GroupMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateGroupWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    final var model = new GroupDbModel.Builder().groupId("group1").name("Updated Name").build();

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GROUP,
                    WriteStatementType.UPDATE,
                    model.groupId(),
                    "io.camunda.db.rdbms.sql.GroupMapper.update",
                    model)));
  }

  @Test
  void shouldAddMember() {
    final var member = new GroupMemberDbModel("group1", "user1", "USER");

    writer.addMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GROUP,
                    WriteStatementType.INSERT,
                    member.groupId(),
                    "io.camunda.db.rdbms.sql.GroupMapper.insertMember",
                    member)));
  }

  @Test
  void shouldRemoveMember() {
    final var member = new GroupMemberDbModel("group1", "user1", "USER");

    writer.removeMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GROUP,
                    WriteStatementType.DELETE,
                    member.groupId(),
                    "io.camunda.db.rdbms.sql.GroupMapper.deleteMember",
                    member)));
  }

  @Test
  void shouldDeleteGroup() {
    final String groupId = "group1";

    writer.delete(groupId);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.GROUP,
                    WriteStatementType.DELETE,
                    groupId,
                    "io.camunda.db.rdbms.sql.GroupMapper.delete",
                    groupId)));
  }
}
