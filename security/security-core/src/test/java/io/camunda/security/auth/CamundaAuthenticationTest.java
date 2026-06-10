/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CamundaAuthenticationTest {

  @Test
  void supplierIsNotInvokedUntilFirstAccessorCall() {
    final var invocations = new AtomicInteger();
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIdsSupplier(
                        () -> {
                          invocations.incrementAndGet();
                          return List.of("g1");
                        }));

    assertThat(invocations).hasValue(0);

    final var groups = auth.authenticatedGroupIds();
    assertThat(groups).containsExactly("g1");
    assertThat(invocations).hasValue(1);
  }

  @Test
  void supplierIsInvokedAtMostOnce() {
    final var invocations = new AtomicInteger();
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .roleIdsSupplier(
                        () -> {
                          invocations.incrementAndGet();
                          return List.of("r1");
                        }));

    auth.authenticatedRoleIds().size();
    auth.authenticatedRoleIds().contains("r1");
    auth.authenticatedRoleIds().iterator().next();

    assertThat(invocations).hasValue(1);
  }

  @Test
  void independentSuppliersForEachMembershipField() {
    final var groupCalls = new AtomicInteger();
    final var roleCalls = new AtomicInteger();
    final var tenantCalls = new AtomicInteger();
    final var mappingRuleCalls = new AtomicInteger();

    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIdsSupplier(
                        () -> {
                          groupCalls.incrementAndGet();
                          return List.of("g");
                        })
                    .roleIdsSupplier(
                        () -> {
                          roleCalls.incrementAndGet();
                          return List.of("r");
                        })
                    .tenantsSupplier(
                        () -> {
                          tenantCalls.incrementAndGet();
                          return List.of("t");
                        })
                    .mappingRulesSupplier(
                        () -> {
                          mappingRuleCalls.incrementAndGet();
                          return List.of("m");
                        }));

    // Only read groups — the other suppliers must stay untouched.
    assertThat(auth.authenticatedGroupIds()).containsExactly("g");

    assertThat(groupCalls).hasValue(1);
    assertThat(roleCalls).hasValue(0);
    assertThat(tenantCalls).hasValue(0);
    assertThat(mappingRuleCalls).hasValue(0);
  }

  @Test
  void buildFailsWhenBothEagerAndSupplierSetOnSameField() {
    assertThatThrownBy(
            () ->
                CamundaAuthentication.of(
                    b ->
                        b.user("alice")
                            .groupIds(List.of("g1"))
                            .groupIdsSupplier(() -> List.of("g2"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("groupIds");
  }

  @Test
  void eagerFieldsRemainUnchanged() {
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIds(List.of("g1", "g2"))
                    .roleIds(List.of("r1"))
                    .tenants(List.of("t1"))
                    .mappingRule(List.of("m1")));

    assertThat(auth.authenticatedGroupIds()).containsExactly("g1", "g2");
    assertThat(auth.authenticatedRoleIds()).containsExactly("r1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("t1");
    assertThat(auth.authenticatedMappingRuleIds()).containsExactly("m1");
  }

  @Test
  void serializationProducesNonLazyList() throws Exception {
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIdsSupplier(() -> List.of("g1", "g2"))
                    .roleIdsSupplier(() -> List.of("r1"))
                    .tenantsSupplier(() -> List.of("t1"))
                    .mappingRulesSupplier(() -> List.of("m1")));

    final var bytes = new ByteArrayOutputStream();
    try (var oos = new ObjectOutputStream(bytes)) {
      oos.writeObject(auth);
    }

    try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      final var deserialized = (CamundaAuthentication) ois.readObject();
      assertThat(deserialized.authenticatedUsername()).isEqualTo("alice");
      assertThat(deserialized.authenticatedGroupIds()).containsExactly("g1", "g2");
      assertThat(deserialized.authenticatedRoleIds()).containsExactly("r1");
      assertThat(deserialized.authenticatedTenantIds()).containsExactly("t1");
      assertThat(deserialized.authenticatedMappingRuleIds()).containsExactly("m1");
    }
  }

  @Test
  void equalsBetweenLazyAndEagerWithSameContent() {
    final var lazy =
        CamundaAuthentication.of(b -> b.user("alice").groupIdsSupplier(() -> List.of("g1", "g2")));
    final var eager = CamundaAuthentication.of(b -> b.user("alice").groupIds(List.of("g1", "g2")));

    assertThat(lazy).isEqualTo(eager);
    assertThat(lazy.hashCode()).isEqualTo(eager.hashCode());
  }
}
