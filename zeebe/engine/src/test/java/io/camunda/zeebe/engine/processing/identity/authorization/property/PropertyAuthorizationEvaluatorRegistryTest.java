/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.identity.authorization.property.evaluator.PropertyAuthorizationEvaluator;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import org.junit.jupiter.api.Test;

/** Unit tests for PropertyAuthorizationEvaluatorRegistry */
final class PropertyAuthorizationEvaluatorRegistryTest {

  @Test
  void shouldReturnEvaluatorForRegisteredType() {
    // Given: Registry with registered evaluator
    final var registry = new PropertyAuthorizationEvaluatorRegistry();
    final var evaluator = mock(PropertyAuthorizationEvaluator.class);
    when(evaluator.resourceType()).thenReturn(AuthorizationResourceType.USER_TASK);

    // When: Registering evaluator
    registry.register(evaluator);

    // Then: Should return evaluator for that type
    assertThat(registry.get(AuthorizationResourceType.USER_TASK)).contains(evaluator);
  }

  @Test
  void shouldReturnEmptyForUnregisteredType() {
    // Given: Empty registry
    final var registry = new PropertyAuthorizationEvaluatorRegistry();

    // When: Querying unregistered type
    final var result = registry.get(AuthorizationResourceType.PROCESS_DEFINITION);

    // Then: Should return empty
    assertThat(result).isEmpty();
  }

  @Test
  void shouldOverwriteExistingEvaluatorForSameType() {
    // Given: Registry with one evaluator
    final var registry = new PropertyAuthorizationEvaluatorRegistry();
    final var evaluator1 = mock(PropertyAuthorizationEvaluator.class);
    final var evaluator2 = mock(PropertyAuthorizationEvaluator.class);
    when(evaluator1.resourceType()).thenReturn(AuthorizationResourceType.USER_TASK);
    when(evaluator2.resourceType()).thenReturn(AuthorizationResourceType.USER_TASK);

    // When: Registering two evaluators for same type
    registry.register(evaluator1);
    registry.register(evaluator2);

    // Then: Should return the latest evaluator
    assertThat(registry.get(AuthorizationResourceType.USER_TASK)).contains(evaluator2);
  }

  @Test
  void shouldSupportMultipleResourceTypes() {
    // Given: Registry with multiple evaluators
    final var registry = new PropertyAuthorizationEvaluatorRegistry();
    final var userTaskEvaluator = mock(PropertyAuthorizationEvaluator.class);
    final var processDefEvaluator = mock(PropertyAuthorizationEvaluator.class);
    when(userTaskEvaluator.resourceType()).thenReturn(AuthorizationResourceType.USER_TASK);
    when(processDefEvaluator.resourceType())
        .thenReturn(AuthorizationResourceType.PROCESS_DEFINITION);

    // When: Registering multiple evaluators
    registry.register(userTaskEvaluator);
    registry.register(processDefEvaluator);

    // Then: Should return correct evaluator for each type
    assertThat(registry.get(AuthorizationResourceType.USER_TASK)).contains(userTaskEvaluator);
    assertThat(registry.get(AuthorizationResourceType.PROCESS_DEFINITION))
        .contains(processDefEvaluator);
  }

  @Test
  void shouldReturnRegistryForFluentChaining() {
    // Given: Empty registry
    final var registry = new PropertyAuthorizationEvaluatorRegistry();
    final var evaluator = mock(PropertyAuthorizationEvaluator.class);
    when(evaluator.resourceType()).thenReturn(AuthorizationResourceType.USER_TASK);

    // When: Registering with fluent API
    final var result = registry.register(evaluator);

    // Then: Should return same registry instance
    assertThat(result).isSameAs(registry);
  }
}
