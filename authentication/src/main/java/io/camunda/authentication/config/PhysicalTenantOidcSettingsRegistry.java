/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * The {@link PhysicalTenantOidcSettings} of every non-default physical tenant, and the one rule
 * that picks the settings for the request's tenant.
 */
final class PhysicalTenantOidcSettingsRegistry {

  private final Map<String, PhysicalTenantOidcSettings> settingsByPhysicalTenant;

  PhysicalTenantOidcSettingsRegistry(
      final Map<String, PhysicalTenantOidcSettings> settingsByPhysicalTenant) {
    this.settingsByPhysicalTenant = Map.copyOf(settingsByPhysicalTenant);
  }

  /**
   * Returns the settings of the current request's physical tenant, or {@code null} for the default
   * tenant and for requests outside any tenant, which the root configuration serves.
   *
   * @throws OAuth2AuthenticationException if the tenant has no resolved configuration; the root
   *     settings are never used for it
   */
  PhysicalTenantOidcSettings current() {
    final var physicalTenantId = PhysicalTenantContext.currentOrNull();
    if (physicalTenantId == null || DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      return null;
    }
    final var settings = settingsByPhysicalTenant.get(physicalTenantId);
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
