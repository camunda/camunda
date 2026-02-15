/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for AuthorizationResult */
final class AuthorizationResultTest {

  @Test
  void shouldReportBothAccessWhenBothTrue() {
    // Given: Both tenant and resource access
    final var result = new AuthorizationResult(true, true);

    // When/Then: Should report both access
    assertThat(result.hasBothAccess()).isTrue();
    assertThat(result.hasTenantAccess()).isTrue();
    assertThat(result.hasResourceAccess()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"false, false", "true, false", "false, true"})
  void shouldReportNoBothAccessWhenEitherFalse(
      final boolean hasTenantAccess, final boolean hasResourceAccess) {
    // Given: At least one access is false
    final var result = new AuthorizationResult(hasTenantAccess, hasResourceAccess);

    // When/Then: Should not report both access
    assertThat(result.hasBothAccess()).isFalse();
  }

  @Test
  void shouldReportTenantAccessIndependently() {
    // Given: Only tenant access
    final var result = new AuthorizationResult(true, false);

    // When/Then: Should report tenant access but not resource access
    assertThat(result.hasTenantAccess()).isTrue();
    assertThat(result.hasResourceAccess()).isFalse();
    assertThat(result.hasBothAccess()).isFalse();
  }

  @Test
  void shouldReportResourceAccessIndependently() {
    // Given: Only resource access
    final var result = new AuthorizationResult(false, true);

    // When/Then: Should report resource access but not tenant access
    assertThat(result.hasTenantAccess()).isFalse();
    assertThat(result.hasResourceAccess()).isTrue();
    assertThat(result.hasBothAccess()).isFalse();
  }

  @Test
  void shouldReportNoAccessWhenBothFalse() {
    // Given: No access at all
    final var result = new AuthorizationResult(false, false);

    // When/Then: Should report no access
    assertThat(result.hasTenantAccess()).isFalse();
    assertThat(result.hasResourceAccess()).isFalse();
    assertThat(result.hasBothAccess()).isFalse();
  }
}
