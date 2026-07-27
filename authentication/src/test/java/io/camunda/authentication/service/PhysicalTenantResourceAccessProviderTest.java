/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.core.authz.ResourceAccessProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhysicalTenantResourceAccessProviderTest {

  @Mock private ResourceAccessProvider defaultProvider;
  @Mock private ResourceAccessProvider tenantAProvider;

  @Test
  void shouldReturnTenantProviderForKnownTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(
            defaultProvider, Map.of("tenanta", tenantAProvider));

    // when / then
    assertThat(provider.withPhysicalTenant("tenanta")).isSameAs(tenantAProvider);
  }

  @Test
  void shouldFallBackToDefaultProviderForUnknownTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(
            defaultProvider, Map.of("tenanta", tenantAProvider));

    // when / then
    assertThat(provider.withPhysicalTenant("tenantb")).isSameAs(defaultProvider);
  }

  @Test
  void shouldFallBackToDefaultProviderForNullTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(
            defaultProvider, Map.of("tenanta", tenantAProvider));

    // when / then — currentOrNull() may yield null off a request thread
    assertThat(provider.withPhysicalTenant(null)).isSameAs(defaultProvider);
  }
}
