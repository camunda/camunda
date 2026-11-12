/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.pt.PhysicalTenantTestSupport.bindPhysicalTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.context.request.RequestContextHolder;

class PhysicalTenantOidcSettingsRegistryTest {

  private static final PhysicalTenantOidcSettings TENANT_A_SETTINGS =
      new PhysicalTenantOidcSettings(null, Map.of(), Map.of());

  private static final PhysicalTenantOidcSettings DEFAULT_SETTINGS =
      new PhysicalTenantOidcSettings(null, Map.of(), Map.of());

  private final PhysicalTenantOidcSettingsRegistry registry =
      new PhysicalTenantOidcSettingsRegistry(
          Map.of("tenanta", TENANT_A_SETTINGS, "default", DEFAULT_SETTINGS));

  @AfterEach
  void clearTenantContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldServeTheRootOutsideAnyTenant() {
    // when
    final var settings = registry.current();

    // then
    assertThat(settings).isNull();
  }

  @Test
  void shouldReturnTheSettingsOfTheDefaultTenantLikeAnyTenant() {
    // given
    bindPhysicalTenant("default");

    // when
    final var settings = registry.current();

    // then
    assertThat(settings).isSameAs(DEFAULT_SETTINGS);
  }

  @Test
  void shouldReturnTheSettingsOfTheCurrentTenant() {
    // given
    bindPhysicalTenant("tenanta");

    // when
    final var settings = registry.current();

    // then
    assertThat(settings).isSameAs(TENANT_A_SETTINGS);
  }

  @Test
  void shouldRejectATenantWithoutResolvedSettingsInsteadOfUsingTheRoot() {
    // given
    bindPhysicalTenant("unknowntenant");

    // when / then
    assertThatThrownBy(registry::current).isInstanceOf(OAuth2AuthenticationException.class);
  }
}
