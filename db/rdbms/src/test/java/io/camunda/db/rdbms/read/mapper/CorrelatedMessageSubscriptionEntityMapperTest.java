/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity.MessageSubscriptionType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class CorrelatedMessageSubscriptionEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final var model =
        new CorrelatedMessageSubscriptionDbModel.Builder()
            .correlationKey("testCorrelationKey")
            .correlationTime(OffsetDateTime.now().plusDays(1))
            .messageKey(123L)
            .messageName("testMessageName")
            .flowNodeId("testFlowNodeId")
            .flowNodeInstanceKey(456L)
            .partitionId(4)
            .processDefinitionId("processDefinitionId")
            .processDefinitionKey(789L)
            .processInstanceKey(1011L)
            .rootProcessInstanceKey(2022L)
            .subscriptionKey(321L)
            .subscriptionType(MessageSubscriptionType.PROCESS_EVENT)
            .tenantId("tenantId")
            .build();

    // When
    final var entity = CorrelatedMessageSubscriptionEntityMapper.toEntity(model);

    // Then
    assertThat(entity).usingRecursiveComparison().isEqualTo(model);
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final var model =
        new CorrelatedMessageSubscriptionDbModel.Builder()
            .correlationKey(null)
            .correlationTime(OffsetDateTime.now())
            .messageKey(123L)
            .messageName(null)
            .flowNodeId(null)
            .flowNodeInstanceKey(456L)
            .partitionId(4)
            .processDefinitionId(null)
            .processDefinitionKey(789L)
            .processInstanceKey(1011L)
            .rootProcessInstanceKey(2022L)
            .subscriptionKey(321L)
            .subscriptionType(MessageSubscriptionType.PROCESS_EVENT)
            .tenantId(null)
            .build();

    // When
    final var entity = CorrelatedMessageSubscriptionEntityMapper.toEntity(model);

    // Then
    assertThat(entity.correlationKey())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.messageKey()).isNotNull();
    assertThat(entity.messageName())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.flowNodeId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.processDefinitionId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.tenantId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
  }
}
