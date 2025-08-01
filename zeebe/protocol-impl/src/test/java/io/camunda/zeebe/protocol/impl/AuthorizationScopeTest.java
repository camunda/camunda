/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import org.junit.jupiter.api.Test;

public class AuthorizationScopeTest {

  @Test
  public void shouldProvideWildcardScope() {
    // given
    final var wildcardScope = AuthorizationScope.WILDCARD;

    // then
    assertThat(wildcardScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ANY);
    assertThat(wildcardScope.getResourceId()).isEqualTo(AuthorizationScope.WILDCARD_CHAR);
  }

  @Test
  public void shouldDetectWildcardId() {
    // given
    final var wildcardId = AuthorizationScope.WILDCARD_CHAR;

    // when
    final var wildcardScope = AuthorizationScope.of(wildcardId);

    // then
    assertThat(wildcardScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ANY);
    assertThat(wildcardScope.getResourceId()).isEqualTo(AuthorizationScope.WILDCARD_CHAR);
  }

  @Test
  public void shouldProvideIdScope() {
    // given
    final var resourceId = "resource-id";

    // when
    final var idScope = AuthorizationScope.id(resourceId);

    // then
    assertThat(idScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(idScope.getResourceId()).isEqualTo(resourceId);
  }

  @Test
  public void shouldDetectIdScope() {
    // given
    final var resourceId = "resource-id";

    // when
    final var idScope = AuthorizationScope.of(resourceId);

    // then
    assertThat(idScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(idScope.getResourceId()).isEqualTo(resourceId);
  }

  @Test
  public void shouldThrowErrorForInvalidId() {
    // given
    final var invalidResourceId = AuthorizationScope.WILDCARD_CHAR;

    // when / then
    assertThatThrownBy(() -> AuthorizationScope.id(invalidResourceId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            ("Resource ID cannot be the wildcard character '%s'. For declaring WILDCARD access, please use the AuthorizationScope.WILDCARD constant.")
                .formatted(invalidResourceId));
  }
}
