/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.Builder;
import io.camunda.search.entities.UserTaskEntity;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;

public class UserTaskEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final UserTaskDbModel dbModel =
        new Builder()
            .key(1L)
            .flowNodeBpmnId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationTime(OffsetDateTime.now())
            .completionTime(OffsetDateTime.now().plusDays(1))
            .assignee("assignee")
            .state(UserTaskDbModel.UserTaskState.CREATED)
            .formKey(1L)
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .elementInstanceKey(1L)
            .tenantId("tenantId")
            .dueDate(OffsetDateTime.now().plusDays(3))
            .followUpDate(OffsetDateTime.now().plusDays(2))
            .candidateGroups(List.of("group1", "group2"))
            .candidateUsers(List.of("user1", "user2"))
            .externalFormReference("externalFormReference")
            .processDefinitionVersion(1)
            .customHeaders(Map.of("key", "value"))
            .priority(1)
            .build();

    // When
    final UserTaskEntity entity = UserTaskEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields(
            "bpmnProcessId",
            "processDefinitionId",
            "processInstanceId",
            "flowNodeInstanceId",
            "customHeaders",
            "creationTime",
            "completionTime",
            "dueDate",
            "followUpDate")
        .isEqualTo(dbModel);

    assertThat(entity.bpmnProcessId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processInstanceId()).isEqualTo(dbModel.processInstanceKey());
    assertThat(entity.flowNodeInstanceId()).isEqualTo(dbModel.elementInstanceKey());
    assertThat(entity.customHeaders()).isEqualTo(Map.of("key", "value"));
    assertThat(entity.creationTime())
        .isCloseTo(dbModel.creationTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.completionTime())
        .isCloseTo(dbModel.completionTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.dueDate())
        .isCloseTo(dbModel.dueDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.followUpDate())
        .isCloseTo(dbModel.followUpDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
  }

  @Test
  public void testToEntityWithNullDates() {
    // Given
    final UserTaskDbModel dbModel =
        new Builder()
            .key(1L)
            .flowNodeBpmnId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationTime(OffsetDateTime.now())
            .completionTime(null)
            .assignee("assignee")
            .state(UserTaskDbModel.UserTaskState.CREATED)
            .formKey(1L)
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .elementInstanceKey(1L)
            .tenantId("tenantId")
            .dueDate(null)
            .followUpDate(null)
            .candidateGroups(List.of("group1", "group2"))
            .candidateUsers(List.of("user1", "user2"))
            .externalFormReference("externalFormReference")
            .processDefinitionVersion(1)
            .customHeaders(Map.of("key", "value"))
            .priority(1)
            .build();

    // When
    final UserTaskEntity entity = UserTaskEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields(
            "bpmnProcessId",
            "processDefinitionId",
            "processInstanceId",
            "flowNodeInstanceId",
            "customHeaders",
            "creationTime",
            "completionTime",
            "dueDate",
            "followUpDate")
        .isEqualTo(dbModel);

    assertThat(entity.bpmnProcessId()).isEqualTo(dbModel.processDefinitionId());
    assertThat(entity.processDefinitionId()).isEqualTo(dbModel.processDefinitionKey());
    assertThat(entity.processInstanceId()).isEqualTo(dbModel.processInstanceKey());
    assertThat(entity.flowNodeInstanceId()).isEqualTo(dbModel.elementInstanceKey());
    assertThat(entity.customHeaders()).isEqualTo(Map.of("key", "value"));
    assertThat(entity.creationTime())
        .isCloseTo(dbModel.creationTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertNull(entity.completionTime());
    assertNull(entity.dueDate());
    assertNull(entity.followUpDate());
  }
}
