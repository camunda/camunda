/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

@ExtendWith(MockitoExtension.class)
public class PersistentAuthorizedClientsConditionTest {
  @Mock private ConditionContext conditionContext;
  @Mock private Environment environment;
  @Mock private AnnotatedTypeMetadata annotatedTypeMetadata;

  private ConditionalOnPersistentAuthorizedClientsEnabled.PersistentAuthorizedClientsCondition
      condition;

  @BeforeEach
  void setUp() {
    condition =
        new ConditionalOnPersistentAuthorizedClientsEnabled.PersistentAuthorizedClientsCondition();
    when(conditionContext.getEnvironment()).thenReturn(environment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "TRUE", "True"})
  void shouldReturnTrueWhenPropertyIsTrue(final String propertyValue) {
    // Given
    when(environment.getProperty("camunda.persistent.authorizedClients.enabled"))
        .thenReturn(propertyValue);

    // When
    final boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

    // Then
    assertTrue(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "FALSE", "False"})
  void shouldReturnFalseWhenPropertyIsFalse(final String propertyValue) {
    // Given
    when(environment.getProperty("camunda.persistent.authorizedClients.enabled"))
        .thenReturn(propertyValue);

    // When
    final boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

    // Then
    assertFalse(result);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid", "1", "0", "yes", "no", "on", "off", "   ", "\t\n"})
  void shouldReturnFalseWhenPropertyIsIncorrect(final String propertyValue) {
    // Given
    when(environment.getProperty("camunda.persistent.authorizedClients.enabled"))
        .thenReturn(propertyValue);

    // When
    final boolean result = condition.matches(conditionContext, annotatedTypeMetadata);

    // Then
    assertFalse(result);
  }
}
