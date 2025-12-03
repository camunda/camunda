/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleWriterTest {

  private ExecutionQueue executionQueue;
  private RoleWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    writer = new RoleWriter(executionQueue);
  }

  @Test
  void shouldCreateRole() {
    final var model =
        new RoleDbModel.Builder()
            .roleId("role1")
            .name("Test Role")
            .description("Description")
            .build();

    writer.create(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldUpdateRoleWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    final var model = new RoleDbModel.Builder().roleId("role1").name("Updated Name").build();

    writer.update(model);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldAddMember() {
    final var member = new RoleMemberDbModel("role1", "user1", "USER");

    writer.addMember(member);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldRemoveMember() {
    final var member = new RoleMemberDbModel("role1", "user1", "USER");

    writer.removeMember(member);

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }

  @Test
  void shouldDeleteRole() {
    writer.delete("role1");

    verify(executionQueue).executeInQueue(any(QueueItem.class));
  }
}
