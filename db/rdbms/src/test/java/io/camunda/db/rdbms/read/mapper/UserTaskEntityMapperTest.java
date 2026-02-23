/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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
            .userTaskKey(1L)
            .elementId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationDate(OffsetDateTime.now())
            .completionDate(OffsetDateTime.now().plusDays(1))
            .assignee("assignee")
            .state(UserTaskDbModel.UserTaskState.CREATED)
            .formKey(2L)
            .processDefinitionKey(3L)
            .processInstanceKey(4L)
            .rootProcessInstanceKey(5L)
            .elementInstanceKey(6L)
            .tenantId("tenantId")
            .dueDate(OffsetDateTime.now().plusDays(3))
            .followUpDate(OffsetDateTime.now().plusDays(2))
            .candidateGroups(List.of("group1", "group2"))
            .candidateUsers(List.of("user1", "user2"))
            .externalFormReference("externalFormReference")
            .processDefinitionVersion(7)
            .customHeaders(Map.of("key", "value"))
            .priority(8)
            .build();

    // When
    final UserTaskEntity entity = UserTaskEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields(
            "customHeaders",
            "creationDate",
            "completionDate",
            "dueDate",
            "followUpDate",
            "processName",
            // will be initialized to empty set in the entity, but is null in the db model
            "tags")
        .isEqualTo(dbModel);

    assertThat(entity.customHeaders()).isEqualTo(Map.of("key", "value"));
    assertThat(entity.creationDate())
        .isCloseTo(dbModel.creationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.completionDate())
        .isCloseTo(dbModel.completionDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.dueDate())
        .isCloseTo(dbModel.dueDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.followUpDate())
        .isCloseTo(dbModel.followUpDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.tags()).isNotNull().isEmpty();
  }

  @Test
  public void testToEntityWithNullDates() {
    // Given
    final UserTaskDbModel dbModel =
        new Builder()
            .userTaskKey(1L)
            .elementId("flowNodeBpmnId")
            .processDefinitionId("processDefinitionId")
            .creationDate(OffsetDateTime.now())
            .completionDate(null)
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
            "customHeaders",
            "creationDate",
            "completionDate",
            "dueDate",
            "followUpDate",
            "processName",
            // will be initialized to empty set in the entity, but is null in the db model
            "tags")
        .isEqualTo(dbModel);

    assertThat(entity.customHeaders()).isEqualTo(Map.of("key", "value"));
    assertThat(entity.creationDate())
        .isCloseTo(dbModel.creationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.completionDate()).isNull();
    assertThat(entity.dueDate()).isNull();
    assertThat(entity.followUpDate()).isNull();
    assertThat(entity.tags()).isNotNull().isEmpty();
  }
}
