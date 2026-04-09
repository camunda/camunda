/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.schema.exceptions.SearchEngineException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class SearchEngineHealthIndicatorTest {

  @Mock private TasklistIndicesCheck indicesCheck;
  @InjectMocks private SearchEngineHealthIndicator healthIndicator;

  @Test
  void shouldReturnUpWhenHealthyAndSchemaExists() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(true);
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.schemaExists()).thenReturn(true);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReturnUpWhenHealthCheckDisabled() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(false);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    verify(indicesCheck).isHealthCheckEnabled();
    verifyNoMoreInteractions(indicesCheck);
  }

  @Test
  void shouldReturnDownWhenNotHealthy() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(true);
    when(indicesCheck.isHealthy()).thenReturn(false);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReturnDownWhenSchemaNotExists() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(true);
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.schemaExists()).thenReturn(false);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReturnDownWithExceptionWhenIsHealthyThrows() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(true);
    when(indicesCheck.isHealthy())
        .thenThrow(new SearchEngineException("master_not_discovered_exception"));

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  @Test
  void shouldReturnDownWithExceptionWhenSchemaExistsThrows() {
    // given
    when(indicesCheck.isHealthCheckEnabled()).thenReturn(true);
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.schemaExists())
        .thenThrow(
            new SearchEngineException(
                "Failed retrieving mappings from index/index templates with pattern"));

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }
}
