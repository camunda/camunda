/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.model.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CamundaAuthenticationTest {

  @Test
  void noneShouldHaveAllEmptyFields() {
    final var auth = CamundaAuthentication.none();
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.anonymousUser()).isFalse();
    assertThat(auth.authenticatedGroupIds()).isEmpty();
    assertThat(auth.authenticatedRoleIds()).isEmpty();
    assertThat(auth.authenticatedTenantIds()).isEmpty();
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
    assertThat(auth.claims()).isNull();
    assertThat(auth.isAnonymous()).isFalse();
  }

  @Test
  void anonymousShouldBeAnonymous() {
    final var auth = CamundaAuthentication.anonymous();
    assertThat(auth.isAnonymous()).isTrue();
    assertThat(auth.anonymousUser()).isTrue();
    assertThat(auth.authenticatedUsername()).isNull();
    assertThat(auth.authenticatedClientId()).isNull();
  }

  @Test
  void builderShouldPopulateAllFields() {
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .group("g1")
                    .groupIds(List.of("g2", "g3"))
                    .role("r1")
                    .roleIds(List.of("r2"))
                    .tenant("t1")
                    .tenants(List.of("t2"))
                    .mappingRule("m1")
                    .mappingRule(List.of("m2"))
                    .claims(Map.of("key", "value")));

    assertThat(auth.authenticatedUsername()).isEqualTo("alice");
    assertThat(auth.authenticatedGroupIds()).containsExactly("g1", "g2", "g3");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1", "r2");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1", "t2");
    assertThat(auth.authenticatedMappingRuleIds()).containsExactly("m1", "m2");
    assertThat(auth.claims()).containsEntry("key", "value");
  }

  @Test
  void builderShouldSupportClientId() {
    final var auth = CamundaAuthentication.of(b -> b.clientId("my-client"));
    assertThat(auth.authenticatedClientId()).isEqualTo("my-client");
    assertThat(auth.authenticatedUsername()).isNull();
  }

  @Test
  void shouldRejectBothUsernameAndClientId() {
    assertThatThrownBy(() -> CamundaAuthentication.of(b -> b.user("alice").clientId("client")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Either username or clientId must be set, not both.");
  }

  @Test
  void listsShouldBeDefensivelyCopied() {
    final var sourceGroups = new ArrayList<>(List.of("g1", "g2"));
    final var auth = CamundaAuthentication.of(b -> b.user("alice").groupIds(sourceGroups));

    sourceGroups.add("g3");

    assertThat(auth.authenticatedGroupIds()).containsExactly("g1", "g2");
  }

  @Test
  void listsShouldBeUnmodifiable() {
    final var auth = CamundaAuthentication.of(b -> b.user("alice").group("g1"));

    assertThatThrownBy(() -> auth.authenticatedGroupIds().add("g2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void ofFactoryMethodShouldApplyBuilderFunction() {
    final var auth = CamundaAuthentication.of(b -> b.user("bob").role("admin"));
    assertThat(auth.authenticatedUsername()).isEqualTo("bob");
    assertThat(auth.authenticatedRoleIds()).containsExactly("admin");
  }

  @Test
  void nullListsShouldBeHandledGracefully() {
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIds(null)
                    .roleIds(null)
                    .tenants(null)
                    .mappingRule((List<String>) null));

    assertThat(auth.authenticatedGroupIds()).isEmpty();
    assertThat(auth.authenticatedRoleIds()).isEmpty();
    assertThat(auth.authenticatedTenantIds()).isEmpty();
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
  }
}
