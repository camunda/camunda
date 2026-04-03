/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import static org.assertj.core.api.Assertions.assertThat;
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
class IndicesHealthIndicatorTest {

  @Mock private IndicesCheck indicesCheck;
  @InjectMocks private IndicesHealthIndicator healthIndicator;

  @Test
  void shouldReturnUpWhenHealthyAndIndicesPresent() {
    // given
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.indicesArePresent()).thenReturn(true);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReturnDownWhenNotHealthy() {
    // given
    when(indicesCheck.isHealthy()).thenReturn(false);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReturnDownWhenIndicesNotPresent() {
    // given
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.indicesArePresent()).thenReturn(false);

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReturnDownWithExceptionWhenIsHealthyThrows() {
    // given
    when(indicesCheck.isHealthy())
        .thenThrow(new SearchEngineException("master_not_discovered_exception"));

    // when
    final Health health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
  }

  @Test
  void shouldReturnDownWithExceptionWhenIndicesPresentThrows() {
    // given
    when(indicesCheck.isHealthy()).thenReturn(true);
    when(indicesCheck.indicesArePresent())
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
