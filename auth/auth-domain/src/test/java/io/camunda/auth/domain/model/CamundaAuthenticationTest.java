/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CamundaAuthenticationTest {

  @Test
  void shouldCreateUserAuthentication() {
    // given
    final var auth =
        new CamundaAuthentication.Builder()
            .user("john")
            .groupIds(List.of("g1", "g2"))
            .roleIds(List.of("r1"))
            .tenants(List.of("t1"))
            .build();

    // then
    assertThat(auth.authenticatedUsername()).isEqualTo("john");
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.anonymousUser()).isFalse();
    assertThat(auth.authenticatedGroupIds()).containsExactly("g1", "g2");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1");
  }

  @Test
  void shouldCreateClientAuthentication() {
    // given
    final var auth = new CamundaAuthentication.Builder().clientId("my-client").build();

    // then
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isEqualTo("my-client");
    assertThat(auth.anonymousUser()).isFalse();
  }

  @Test
  void shouldCreateAnonymousAuthentication() {
    // when
    final var auth = CamundaAuthentication.anonymous();

    // then
    assertThat(auth.anonymousUser()).isTrue();
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.authenticatedGroupIds()).isEmpty();
    assertThat(auth.authenticatedRoleIds()).isEmpty();
    assertThat(auth.authenticatedTenantIds()).isEmpty();
  }

  @Test
  void shouldCreateNoneAuthentication() {
    // when
    final var auth = CamundaAuthentication.none();

    // then
    assertThat(auth.anonymousUser()).isFalse();
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isNull();
  }

  @Test
  void shouldCreateWithStaticOfMethod() {
    // when
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("user1")
                    .groupIds(List.of("g1"))
                    .roleIds(List.of("r1"))
                    .tenants(List.of("t1"))
                    .mappingRule(List.of("m1"))
                    .claims(Map.of("sub", "user1")));

    // then
    assertThat(auth.authenticatedUsername()).isEqualTo("user1");
    assertThat(auth.authenticatedGroupIds()).containsExactly("g1");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1");
    assertThat(auth.authenticatedMappingRuleIds()).containsExactly("m1");
    assertThat(auth.claims()).containsEntry("sub", "user1");
    assertThat(auth.anonymousUser()).isFalse();
  }

  @Test
  void shouldPreserveClaims() {
    // given
    final Map<String, Object> claims = Map.of("sub", "user1", "iss", "https://example.com");
    final var auth = new CamundaAuthentication.Builder().user("user1").claims(claims).build();

    // then
    assertThat(auth.claims()).containsEntry("sub", "user1");
    assertThat(auth.claims()).containsEntry("iss", "https://example.com");
  }

  @Test
  void shouldDefaultToEmptyLists() {
    // given
    final var auth = new CamundaAuthentication.Builder().user("user1").build();

    // then
    assertThat(auth.authenticatedGroupIds()).isEmpty();
    assertThat(auth.authenticatedRoleIds()).isEmpty();
    assertThat(auth.authenticatedTenantIds()).isEmpty();
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
  }
}
