/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.core.authz.ResourceAccessProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhysicalTenantResourceAccessProviderTest {

  @Mock private ResourceAccessProvider tenantAProvider;

  @Test
  void shouldReturnTenantProviderForKnownTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(Map.of("tenanta", tenantAProvider));

    // when / then
    assertThat(provider.withPhysicalTenant("tenanta")).isSameAs(tenantAProvider);
  }

  @Test
  void shouldFailHardForUnknownTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(Map.of("tenanta", tenantAProvider));

    // when / then — no silent fallback: an unknown tenant would break isolation, so fail hard
    assertThatThrownBy(() -> provider.withPhysicalTenant("tenantb"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenantb");
  }

  @Test
  void shouldFailHardForNullTenant() {
    // given
    final var provider =
        new PhysicalTenantResourceAccessProvider(Map.of("tenanta", tenantAProvider));

    // when / then — an unresolved physical tenant (null) is a configuration error, not a default
    assertThatThrownBy(() -> provider.withPhysicalTenant(null))
        .isInstanceOf(IllegalStateException.class);
  }
}
