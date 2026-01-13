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
            .historyCleanupDate(OffsetDateTime.now().plusDays(3))
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
}
