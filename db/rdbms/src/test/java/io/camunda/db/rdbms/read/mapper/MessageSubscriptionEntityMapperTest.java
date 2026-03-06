/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class MessageSubscriptionEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final var model =
        new MessageSubscriptionDbModel.Builder()
            .messageSubscriptionKey(1L)
            .processDefinitionId("processDefinitionId")
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .flowNodeId("flowNodeId")
            .flowNodeInstanceKey(1L)
            .messageSubscriptionState(MessageSubscriptionState.CREATED)
            .dateTime(OffsetDateTime.now().plusDays(1))
            .messageName("testMessageName")
            .correlationKey("testCorrelationKey")
            .tenantId("tenantId")
            .build();

    // When
    final var entity = MessageSubscriptionEntityMapper.toEntity(model);

    // Then
    assertThat(entity).usingRecursiveComparison().isEqualTo(model);
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final var model =
        new MessageSubscriptionDbModel.Builder()
            .messageSubscriptionKey(1L)
            .processDefinitionId(null)
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .flowNodeId(null)
            .flowNodeInstanceKey(1L)
            .messageSubscriptionState(MessageSubscriptionState.CREATED)
            .dateTime(null)
            .messageName(null)
            .correlationKey(null)
            .tenantId(null)
            .build();

    // When
    final var entity = MessageSubscriptionEntityMapper.toEntity(model);

    // Then
    assertThat(entity.messageSubscriptionKey()).isNotNull();
    assertThat(entity.processDefinitionId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.flowNodeId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.dateTime()).isNull();
    assertThat(entity.messageName())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.correlationKey())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.tenantId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
  }
}
