/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.pt.PhysicalTenantAuthentications;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * The {@link PhysicalTenantOidcSettings} of every physical tenant, and the one rule that picks the
 * settings for the request's tenant. A tenant's settings are looked up per request, so they follow
 * the configuration its HTTP security serves.
 */
final class PhysicalTenantOidcSettingsRegistry {

  private final Function<String, Optional<PhysicalTenantOidcSettings>> settingsByPhysicalTenant;

  PhysicalTenantOidcSettingsRegistry(
      final Map<String, PhysicalTenantOidcSettings> settingsByPhysicalTenant) {
    final var settings = Map.copyOf(settingsByPhysicalTenant);
    this.settingsByPhysicalTenant = id -> Optional.ofNullable(settings.get(id));
  }

  PhysicalTenantOidcSettingsRegistry(
      final PhysicalTenantAuthentications.Derived<PhysicalTenantOidcSettings>
          settingsByPhysicalTenant) {
    this.settingsByPhysicalTenant = settingsByPhysicalTenant::get;
  }

  /**
   * Returns the settings of the current request's physical tenant, the {@code default} tenant
   * included, or {@code null} for a request outside any tenant, which the root configuration
   * serves.
   *
   * @throws OAuth2AuthenticationException if the tenant has no resolved configuration; the root
   *     settings are never used for it
   */
  PhysicalTenantOidcSettings current() {
    final var physicalTenantId = PhysicalTenantContext.currentOrNull();
    if (physicalTenantId == null) {
      return null;
    }
    final var settings = settingsByPhysicalTenant.apply(physicalTenantId).orElse(null);
    if (settings == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "No OIDC configuration for physical tenant '" + physicalTenantId + "'",
              null));
    }
    return settings;
  }
}
