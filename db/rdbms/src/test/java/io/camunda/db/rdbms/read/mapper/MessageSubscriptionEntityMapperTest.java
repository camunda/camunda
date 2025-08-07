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
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
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
            .messageSubscriptionType(MessageSubscriptionType.CORRELATED)
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
}
