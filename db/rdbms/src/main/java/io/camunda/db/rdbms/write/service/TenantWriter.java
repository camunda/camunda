/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.Function;

public class TenantWriter {

  private final ExecutionQueue executionQueue;

  public TenantWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final TenantDbModel tenant) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            WriteStatementType.INSERT,
            tenant.tenantId(),
            "io.camunda.db.rdbms.sql.TenantMapper.insert",
            tenant));
  }

  public void update(final TenantDbModel tenant) {
    final boolean wasMerged =
        mergeToQueue(
            tenant.tenantId(),
            b ->
                b.tenantId(tenant.tenantId())
                    .name(tenant.name())
                    .description(tenant.description()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.TENANT,
              WriteStatementType.UPDATE,
              tenant.tenantId(),
              "io.camunda.db.rdbms.sql.TenantMapper.update",
              tenant));
    }
  }

  public void addMember(final TenantMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            WriteStatementType.INSERT,
            member.tenantId(),
            "io.camunda.db.rdbms.sql.TenantMapper.insertMember",
            member));
  }

  public void removeMember(final TenantMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            WriteStatementType.DELETE,
            member.tenantId(),
            "io.camunda.db.rdbms.sql.TenantMapper.deleteMember",
            member));
  }

  public void delete(final TenantDbModel tenant) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            WriteStatementType.DELETE,
            tenant.tenantId(),
            "io.camunda.db.rdbms.sql.TenantMapper.delete",
            tenant.tenantId()));
  }

  private boolean mergeToQueue(
      final String tenantId,
      final Function<TenantDbModel.Builder, TenantDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.TENANT, tenantId, TenantDbModel.class, mergeFunction));
  }
}
