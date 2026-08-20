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

import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class TenantWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final TenantWriter writer = new TenantWriter(executionQueue);

  @Test
  void shouldCreateTenant() {
    final var model =
        new TenantDbModel.Builder()
            .tenantId("tenant1")
            .name("Test Tenant")
            .description("Description")
            .build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.TENANT,
                    WriteStatementType.INSERT,
                    model.tenantId(),
                    "io.camunda.db.rdbms.sql.TenantMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateTenantWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    final var model = new TenantDbModel.Builder().tenantId("tenant1").name("Updated Name").build();

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.TENANT,
                    WriteStatementType.UPDATE,
                    model.tenantId(),
                    "io.camunda.db.rdbms.sql.TenantMapper.update",
                    model)));
  }

  @Test
  void shouldAddMember() {
    final var member = new TenantMemberDbModel("tenant1", "user1", "USER");

    writer.addMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.TENANT,
                    WriteStatementType.INSERT,
                    member.tenantId(),
                    "io.camunda.db.rdbms.sql.TenantMapper.insertMember",
                    member)));
  }

  @Test
  void shouldRemoveMember() {
    final var member = new TenantMemberDbModel("tenant1", "user1", "USER");

    writer.removeMember(member);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.TENANT,
                    WriteStatementType.DELETE,
                    member.tenantId(),
                    "io.camunda.db.rdbms.sql.TenantMapper.deleteMember",
                    member)));
  }

  @Test
  void shouldDeleteTenant() {
    final var model = new TenantDbModel.Builder().tenantId("tenant1").build();

    writer.delete(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.TENANT,
                    WriteStatementType.DELETE,
                    model.tenantId(),
                    "io.camunda.db.rdbms.sql.TenantMapper.delete",
                    model.tenantId())));
  }
}
