/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.PrincipalType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoOpMembershipResolverTest {

  @Test
  void shouldResolveUserPrincipal() {
    // given
    final var resolver = new NoOpMembershipResolver();
    final Map<String, Object> claims = Map.of("sub", "john");

    // when
    final CamundaAuthentication auth = resolver.resolveMemberships(claims, "john", PrincipalType.USER);

    // then
    assertThat(auth.authenticatedUsername()).isEqualTo("john");
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.authenticatedGroupIds()).isEmpty();
    assertThat(auth.claims()).containsEntry("sub", "john");
  }

  @Test
  void shouldResolveClientPrincipal() {
    // given
    final var resolver = new NoOpMembershipResolver();
    final Map<String, Object> claims = Map.of("azp", "my-service");

    // when
    final CamundaAuthentication auth =
        resolver.resolveMemberships(claims, "my-service", PrincipalType.CLIENT);

    // then
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isEqualTo("my-service");
    assertThat(auth.authenticatedGroupIds()).isEmpty();
  }

  @Test
  void shouldExtractGroupsFromClaimsWhenConfigured() {
    // given
    final var resolver = new NoOpMembershipResolver("groups");
    final Map<String, Object> claims =
        Map.of("sub", "john", "groups", List.of("admin", "users"));

    // when
    final CamundaAuthentication auth = resolver.resolveMemberships(claims, "john", PrincipalType.USER);

    // then
    assertThat(auth.authenticatedGroupIds()).containsExactly("admin", "users");
  }

  @Test
  void shouldHandleStringGroupsClaim() {
    // given
    final var resolver = new NoOpMembershipResolver("groups");
    final Map<String, Object> claims = Map.of("sub", "john", "groups", "admin");

    // when
    final CamundaAuthentication auth = resolver.resolveMemberships(claims, "john", PrincipalType.USER);

    // then
    assertThat(auth.authenticatedGroupIds()).containsExactly("admin");
  }

  @Test
  void shouldReturnEmptyGroupsWhenClaimMissing() {
    // given
    final var resolver = new NoOpMembershipResolver("groups");
    final Map<String, Object> claims = Map.of("sub", "john");

    // when
    final CamundaAuthentication auth = resolver.resolveMemberships(claims, "john", PrincipalType.USER);

    // then
    assertThat(auth.authenticatedGroupIds()).isEmpty();
  }

  @Test
  void shouldReturnEmptyGroupsWhenNoGroupsClaimConfigured() {
    // given
    final var resolver = new NoOpMembershipResolver();
    final Map<String, Object> claims =
        Map.of("sub", "john", "groups", List.of("admin"));

    // when
    final CamundaAuthentication auth = resolver.resolveMemberships(claims, "john", PrincipalType.USER);

    // then
    assertThat(auth.authenticatedGroupIds()).isEmpty();
  }
}
