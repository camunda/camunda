/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class AuthorizationScopeTest {

  @Test
  public void shouldProvideWildcardScope() {
    // given
    final AuthorizationScope wildcardScope = AuthorizationScope.WILDCARD;

    // then
    assertThat(wildcardScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ANY);
    assertThat(wildcardScope.getResourceId()).isEqualTo(AuthorizationScope.WILDCARD_CHAR);
  }

  @Test
  public void shouldDetectWildcardId() {
    // given
    final String wildcardId = AuthorizationScope.WILDCARD_CHAR;

    // when
    final AuthorizationScope wildcardScope = AuthorizationScope.of(wildcardId);

    // then
    assertThat(wildcardScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ANY);
    assertThat(wildcardScope.getResourceId()).isEqualTo(AuthorizationScope.WILDCARD_CHAR);
  }

  @Test
  public void shouldProvideIdScope() {
    // given
    final String resourceId = "resource-id";

    // when
    final AuthorizationScope idScope = AuthorizationScope.id(resourceId);

    // then
    assertThat(idScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(idScope.getResourceId()).isEqualTo(resourceId);
  }

  @Test
  public void shouldDetectIdScope() {
    // given
    final String resourceId = "resource-id";

    // when
    final AuthorizationScope idScope = AuthorizationScope.of(resourceId);

    // then
    assertThat(idScope.getMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(idScope.getResourceId()).isEqualTo(resourceId);
  }

  @Test
  public void shouldThrowErrorForInvalidId() {
    // given
    final String invalidResourceId = AuthorizationScope.WILDCARD_CHAR;

    // when / then
    assertThatThrownBy(() -> AuthorizationScope.id(invalidResourceId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            String.format(
                "Resource ID cannot be the wildcard character '%s'. For declaring WILDCARD access, please use the AuthorizationScope.WILDCARD constant.",
                invalidResourceId));
  }
}
