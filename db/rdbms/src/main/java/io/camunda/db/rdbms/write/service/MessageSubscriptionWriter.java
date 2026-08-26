/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;

public class MessageSubscriptionWriter extends ProcessInstanceDependant implements RdbmsWriter {

  private final MessageSubscriptionMapper mapper;
  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public MessageSubscriptionWriter(
      final ExecutionQueue executionQueue,
      final MessageSubscriptionMapper mapper,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    super(mapper);
    this.mapper = mapper;
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final MessageSubscriptionDbModel messageSubscription) {
    final var truncated =
        messageSubscription.truncateToolFields(
            vendorDatabaseProperties.userCharColumnSize(),
            vendorDatabaseProperties.charColumnMaxBytes());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            truncated.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.insert",
            truncated));
  }

  /**
   * Idempotent upsert for the CREATED intent. A process message subscription can receive CREATED
   * more than once — the suspend/resume flow reuses the same key and re-emits CREATED when the
   * resume manifest is restored and when the subscription is reopened. A plain {@link #create}
   * would violate the primary key on the second CREATED. This variant inserts the row when absent
   * and, when a row with the same key already exists, refreshes all columns to the incoming CREATED
   * projection. The refresh is required, not cosmetic: a reusable non-interrupting subscription may
   * already be stored as CORRELATED, and the suspend-close ack re-emits CREATED to restore it to
   * OPENED — a no-op-on-conflict would leave the stale CORRELATED state behind.
   */
  public void createIfNotExists(final MessageSubscriptionDbModel messageSubscription) {
    final var truncated =
        messageSubscription.truncateToolFields(
            vendorDatabaseProperties.userCharColumnSize(),
            vendorDatabaseProperties.charColumnMaxBytes());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            truncated.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.createIfNotExists",
            truncated));
  }

  public void update(final MessageSubscriptionDbModel messageSubscription) {
    final var truncated =
        messageSubscription.truncateToolFields(
            vendorDatabaseProperties.userCharColumnSize(),
            vendorDatabaseProperties.charColumnMaxBytes());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.MESSAGE_SUBSCRIPTION,
            WriteStatementType.UPDATE,
            truncated.messageSubscriptionKey(),
            "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.update",
            truncated));
  }

  public int deleteStartEventSubscriptionsByProcessDefinitionKeys(
      final List<Long> processDefinitionKeys, final int limit) {
    return mapper.deleteStartEventSubscriptionsByProcessDefinitionKeys(
        processDefinitionKeys, limit);
  }
}
