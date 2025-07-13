/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultTenantAccessProviderTest {

  private final DefaultTenantAccessProvider tenantAccessProvider =
      new DefaultTenantAccessProvider();

  @Test
  void shouldAllowAccessToTenantIds() {
    // given
    final var authentication =
        CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar", "baz")));

    // when
    final var result = tenantAccessProvider.resolveTenantAccess(authentication);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("bar", "baz");
  }

  @Test
  void shouldDenyTenantAccess() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));

    // when
    final var result = tenantAccessProvider.resolveTenantAccess(authentication);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).isNull();
  }

  @Test
  void shouldDenyTenantAccessWhenTenantIdsIsEmpty() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of()));

    // when
    final var result = tenantAccessProvider.resolveTenantAccess(authentication);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).isNull();
  }
}
