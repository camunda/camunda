/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditLogPropertyMatcherTest {

  private AuditLogPropertyMatcher matcher;

  @BeforeEach
  void setUp() {
    matcher = new AuditLogPropertyMatcher();
  }

  @Test
  void shouldReturnAuditLogEntityClass() {
    // when
    final var resourceClass = matcher.getResourceClass();

    // then
    assertThat(resourceClass).isEqualTo(AuditLogEntity.class);
  }

  @ParameterizedTest(name = "category=''{0}'' should match")
  @MethodSource("provideCategorySuccessfulCases")
  void shouldMatchForAuthorizedCategories(final AuditLogOperationCategory category) {
    // given
    final var auditLog = createAuditLog(category);
    final var authentication = CamundaAuthentication.anonymous();
    final var propertyNames = Set.of(Authorization.PROP_CATEGORY);

    // when
    final var matches = matcher.matches(auditLog, propertyNames, authentication);

    // then
    assertThat(matches).isTrue();
  }

  @ParameterizedTest(name = "category=''{0}'' should not match")
  @MethodSource("provideCategoryNegativeCases")
  void shouldNotMatchForAuthorizedCategories(final AuditLogOperationCategory category) {
    // given
    final var auditLog = createAuditLog(category);
    final var authentication = CamundaAuthentication.anonymous();
    final var propertyNames = Set.of(Authorization.PROP_CATEGORY);

    // when
    final var matches = matcher.matches(auditLog, propertyNames, authentication);

    // then
    assertThat(matches).isFalse();
  }

  static Stream<Arguments> provideCategorySuccessfulCases() {
    return Stream.of(
        Arguments.of(AuditLogOperationCategory.ADMIN),
        Arguments.of(AuditLogOperationCategory.DEPLOYED_RESOURCES),
        Arguments.of(AuditLogOperationCategory.USER_TASKS));
  }

  static Stream<Arguments> provideCategoryNegativeCases() {
    return Stream.of(Arguments.of(AuditLogOperationCategory.UNKNOWN));
  }

  private AuditLogEntity createAuditLog(final AuditLogOperationCategory category) {
    final var auditLog = mock(AuditLogEntity.class);
    when(auditLog.category()).thenReturn(category);
    return auditLog;
  }
}
