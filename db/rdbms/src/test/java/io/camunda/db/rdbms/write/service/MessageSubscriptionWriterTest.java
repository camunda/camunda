/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class MessageSubscriptionWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final MessageSubscriptionMapper mapper = mock(MessageSubscriptionMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final MessageSubscriptionWriter writer =
      new MessageSubscriptionWriter(executionQueue, mapper, vendorDatabaseProperties);

  MessageSubscriptionWriterTest() {
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(Integer.MAX_VALUE);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(null);
  }

  @Test
  void shouldCreateMessageSubscription() {
    final var model = new MessageSubscriptionDbModel.Builder().messageSubscriptionKey(123L).build();

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.MESSAGE_SUBSCRIPTION,
                    WriteStatementType.INSERT,
                    model.messageSubscriptionKey(),
                    "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.insert",
                    model)));
  }

  @Test
  void shouldUpdateMessageSubscription() {
    final var model = new MessageSubscriptionDbModel.Builder().messageSubscriptionKey(123L).build();

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.MESSAGE_SUBSCRIPTION,
                    WriteStatementType.UPDATE,
                    model.messageSubscriptionKey(),
                    "io.camunda.db.rdbms.sql.MessageSubscriptionMapper.update",
                    model)));
  }

  @Test
  void shouldNotTruncateToolFieldsWhenWithinLimit() {
    // given
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(100);
    final var model =
        new MessageSubscriptionDbModel.Builder()
            .messageSubscriptionKey(1L)
            .toolName("short")
            .inboundConnectorType("short")
            .build();

    // when
    writer.create(model);

    // then
    assertThat(model.toolName()).isEqualTo("short");
    assertThat(model.inboundConnectorType()).isEqualTo("short");
  }

  @Test
  void shouldTruncateToolNameAndInboundConnectorTypeOnCreateWhenExceedingCharLimit() {
    // given
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(5);
    final var model =
        new MessageSubscriptionDbModel.Builder()
            .messageSubscriptionKey(1L)
            .toolName("toolname-exceeds-limit")
            .inboundConnectorType("connector-exceeds-limit")
            .build();

    // when
    writer.create(model);

    // then
    assertThat(model.toolName()).hasSize(5);
    assertThat(model.inboundConnectorType()).hasSize(5);
  }

  @Test
  void shouldTruncateToolFieldsOnUpdateWhenExceedingCharLimit() {
    // given
    when(vendorDatabaseProperties.userCharColumnSize()).thenReturn(5);
    final var model =
        new MessageSubscriptionDbModel.Builder()
            .messageSubscriptionKey(1L)
            .toolName("toolname-exceeds-limit")
            .inboundConnectorType("connector-exceeds-limit")
            .build();

    // when
    writer.update(model);

    // then
    assertThat(model.toolName()).hasSize(5);
    assertThat(model.inboundConnectorType()).hasSize(5);
  }
}
