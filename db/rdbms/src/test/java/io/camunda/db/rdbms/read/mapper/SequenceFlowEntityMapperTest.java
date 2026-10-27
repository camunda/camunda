/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import org.junit.jupiter.api.Test;

public class SequenceFlowEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final var dbModel =
        new SequenceFlowDbModel.Builder()
            .flowNodeId("flowNodeId")
            .processInstanceKey(1L)
            .rootProcessInstanceKey(2L)
            .processDefinitionKey(3L)
            .processDefinitionId("processDefinitionId")
            .tenantId("tenantId")
            .partitionId(1)
            .build();

    // When
    final var entity = SequenceFlowEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity.sequenceFlowId()).isEqualTo("1_flowNodeId");
    assertThat(entity.flowNodeId()).isEqualTo("flowNodeId");
    assertThat(entity.processInstanceKey()).isEqualTo(1L);
    assertThat(entity.rootProcessInstanceKey()).isEqualTo(2L);
    assertThat(entity.processDefinitionKey()).isEqualTo(3L);
    assertThat(entity.processDefinitionId()).isEqualTo("processDefinitionId");
    assertThat(entity.tenantId()).isEqualTo("tenantId");
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final var dbModel =
        new SequenceFlowDbModel.Builder()
            .flowNodeId(null)
            .processInstanceKey(1L)
            .rootProcessInstanceKey(2L)
            .processDefinitionKey(3L)
            .processDefinitionId(null)
            .tenantId(null)
            .partitionId(1)
            .build();

    // When
    final var entity = SequenceFlowEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity.flowNodeId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.processDefinitionId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.tenantId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.processInstanceKey()).isEqualTo(1L);
    assertThat(entity.rootProcessInstanceKey()).isEqualTo(2L);
    assertThat(entity.processDefinitionKey()).isEqualTo(3L);
  }
}
