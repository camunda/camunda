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
import java.util.function.Function;

public class TenantWriter {

  private final ExecutionQueue executionQueue;

  public TenantWriter(final ExecutionQueue executionQueue) {
    this.executionQueue = executionQueue;
  }

  public void create(final TenantDbModel flowNode) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            flowNode.tenantKey(),
            "io.camunda.db.rdbms.sql.TenantMapper.insert",
            flowNode));
  }

  public void update(final TenantDbModel tenant) {
    final boolean wasMerged =
        mergeToQueue(tenant.tenantKey(), b -> b.tenantId(tenant.tenantId()).name(tenant.name()));

    if (!wasMerged) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.TENANT,
              tenant.tenantKey(),
              "io.camunda.db.rdbms.sql.TenantMapper.update",
              tenant));
    }
  }

  public void addMember(final TenantMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            member.tenantKey(),
            "io.camunda.db.rdbms.sql.TenantMapper.insertMember",
            member));
  }

  public void removeMember(final TenantMemberDbModel member) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            member.tenantKey(),
            "io.camunda.db.rdbms.sql.TenantMapper.deleteMember",
            member));
  }

  public void delete(final long tenantKey) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            tenantKey,
            "io.camunda.db.rdbms.sql.TenantMapper.delete",
            tenantKey));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.TENANT,
            tenantKey,
            "io.camunda.db.rdbms.sql.TenantMapper.deleteAllMembers",
            tenantKey));
  }

  private boolean mergeToQueue(
      final long key, final Function<TenantDbModel.Builder, TenantDbModel.Builder> mergeFunction) {
    return executionQueue.tryMergeWithExistingQueueItem(
        new UpsertMerger<>(ContextType.TENANT, key, TenantDbModel.class, mergeFunction));
  }
}
