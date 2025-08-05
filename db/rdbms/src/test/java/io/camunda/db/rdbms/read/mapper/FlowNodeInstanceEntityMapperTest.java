/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;

public class FlowNodeInstanceEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final FlowNodeInstanceDbModel flowNodeInstanceDbModel =
        new FlowNodeInstanceDbModelBuilder()
            .flowNodeInstanceKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .processDefinitionId("processDefinitionId")
            .flowNodeScopeKey(4L)
            .startDate(OffsetDateTime.now())
            .endDate(OffsetDateTime.now().plusDays(1))
            .flowNodeId("flowNodeId")
            .flowNodeName("flowNodeName")
            .treePath("element1/element2/element3")
            .type(FlowNodeType.CALL_ACTIVITY)
            .state(FlowNodeState.ACTIVE)
            .incidentKey(5L)
            .numSubprocessIncidents(6L)
            .hasIncident(true)
            .tenantId("tenantId")
            .partitionId(7)
            .historyCleanupDate(OffsetDateTime.now().plusDays(1))
            .build();

    // When
    final FlowNodeInstanceEntity entity =
        FlowNodeInstanceEntityMapper.toEntity(flowNodeInstanceDbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields("startDate", "endDate", "level")
        .isEqualTo(flowNodeInstanceDbModel);

    assertThat(entity.startDate())
        .isCloseTo(
            flowNodeInstanceDbModel.startDate(),
            new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.endDate())
        .isCloseTo(
            flowNodeInstanceDbModel.endDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final FlowNodeInstanceDbModel flowNodeInstanceDbModel =
        new FlowNodeInstanceDbModelBuilder()
            .flowNodeInstanceKey(1L)
            .processInstanceKey(2L)
            .processDefinitionKey(3L)
            .processDefinitionId(null)
            .flowNodeScopeKey(4L)
            .startDate(null)
            .endDate(null)
            .flowNodeId(null)
            .flowNodeName(null)
            .treePath(null)
            .type(FlowNodeType.CALL_ACTIVITY)
            .state(FlowNodeState.ACTIVE)
            .incidentKey(5L)
            .numSubprocessIncidents(null)
            .hasIncident(true)
            .tenantId(null)
            .partitionId(0)
            .historyCleanupDate(null)
            .build();

    // When
    final FlowNodeInstanceEntity entity =
        FlowNodeInstanceEntityMapper.toEntity(flowNodeInstanceDbModel);

    // Then
    assertThat(entity.flowNodeInstanceKey()).isNotNull();
    assertThat(entity.processInstanceKey()).isNotNull();
    assertThat(entity.processDefinitionKey()).isNotNull();
    assertThat(entity.processDefinitionId()).isNull();
    assertThat(entity.startDate()).isNull();
    assertThat(entity.endDate()).isNull();
    assertThat(entity.flowNodeId()).isNull();
    assertThat(entity.flowNodeName()).isNull();
    assertThat(entity.treePath()).isNull();
    assertThat(entity.type()).isNotNull();
    assertThat(entity.state()).isNotNull();
    assertThat(entity.incidentKey()).isNotNull();
    assertThat(entity.hasIncident()).isNotNull();
    assertThat(entity.tenantId()).isNull();
  }
}
