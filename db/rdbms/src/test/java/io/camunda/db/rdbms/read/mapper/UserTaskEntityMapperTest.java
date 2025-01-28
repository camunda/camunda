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

import io.camunda.db.rdbms.fixtures.UserTaskFixtures;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.search.entities.UserTaskEntity;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;

public class UserTaskEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final UserTaskDbModel dbModel = UserTaskFixtures.createRandomized(b -> b);

    // When
    final UserTaskEntity entity = UserTaskEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields(
            "customHeaders", "creationDate", "completionDate", "dueDate", "followUpDate")
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
  }

  @Test
  public void testToEntityWithNullDates() {
    // Given
    final UserTaskDbModel dbModel =
        UserTaskFixtures.createRandomized(
            b -> b.completionDate(null).dueDate(null).followUpDate(null));

    // When
    final UserTaskEntity entity = UserTaskEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields(
            "customHeaders", "creationDate", "completionDate", "dueDate", "followUpDate")
        .isEqualTo(dbModel);

    assertThat(entity.customHeaders()).isEqualTo(Map.of("key", "value"));
    assertThat(entity.creationDate())
        .isCloseTo(dbModel.creationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertNull(entity.completionDate());
    assertNull(entity.dueDate());
    assertNull(entity.followUpDate());
  }
}
