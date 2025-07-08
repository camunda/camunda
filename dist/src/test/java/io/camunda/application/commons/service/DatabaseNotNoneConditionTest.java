/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.mockito.Mockito;

class DatabaseNotNoneConditionTest {

  private final DatabaseNotNoneCondition condition = new DatabaseNotNoneCondition();
  private final AnnotatedTypeMetadata metadata = Mockito.mock(AnnotatedTypeMetadata.class);

  @Test
  void shouldReturnTrueWhenDatabaseTypeIsNotSet() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnTrueWhenDatabaseTypeIsElasticsearch() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.database.type", "elasticsearch");
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnTrueWhenDatabaseTypeIsOpensearch() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.database.type", "opensearch");
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseWhenDatabaseTypeIsNone() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.database.type", "none");
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnFalseWhenDatabaseTypeIsNoneCaseInsensitive() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.database.type", "NONE");
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldReturnTrueWhenDatabaseTypeIsEmpty() {
    // given
    final MockEnvironment environment = new MockEnvironment();
    environment.setProperty("camunda.database.type", "");
    final ConditionContext context = Mockito.mock(ConditionContext.class);
    Mockito.when(context.getEnvironment()).thenReturn(environment);

    // when
    final boolean result = condition.matches(context, metadata);

    // then
    assertThat(result).isTrue();
  }
}