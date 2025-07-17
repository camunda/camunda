/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.TenantOwnedEntity;
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

  @Test
  void shouldAllowAccessToTenant() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var resource = new TenantOwnedTestResource("id", "value", "bar");

    // when
    final var result = tenantAccessProvider.hasTenantAccess(authentication, resource);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("bar");
  }

  @Test
  void shouldDenyAccessToTenant() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var resource = new TenantOwnedTestResource("id", "value", "baz");

    // when
    final var result = tenantAccessProvider.hasTenantAccess(authentication, resource);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("baz");
  }

  @Test
  void shouldDenyAccessToTenantIfNoAuthenticatedTenantsExist() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var resource = new TenantOwnedTestResource("id", "value", "baz");

    // when
    final var result = tenantAccessProvider.hasTenantAccess(authentication, resource);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("baz");
  }

  @Test
  void shouldAllowAccessToNotTenantOwnedEntity() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var resource = new NotTenantOwnedTestResource("id", "value");

    // when
    final var result = tenantAccessProvider.hasTenantAccess(authentication, resource);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).isEmpty();
  }

  @Test
  void shouldAllowAccessToTenantByTenantId() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));

    // when
    final var result = tenantAccessProvider.hasTenantAccessByTenantId(authentication, "bar");

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("bar");
  }

  @Test
  void shouldDenyAccessToTenantByTenantId() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));

    // when
    final var result = tenantAccessProvider.hasTenantAccessByTenantId(authentication, "baz");

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("baz");
  }

  @Test
  void shouldDenyAccessToTenantIfNoAuthenticatedTenantsExistByTenantId() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));

    // when
    final var result = tenantAccessProvider.hasTenantAccessByTenantId(authentication, "baz");

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.tenantIds()).containsExactlyInAnyOrder("baz");
  }

  record TenantOwnedTestResource(String id, String anotherValue, String tenantId)
      implements TenantOwnedEntity {}

  record NotTenantOwnedTestResource(String id, String anotherValue) {}
}
