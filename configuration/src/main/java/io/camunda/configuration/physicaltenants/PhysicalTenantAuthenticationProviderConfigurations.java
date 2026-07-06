/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.env.Environment;

/**
 * Per-tenant {@link OidcProvidersConfiguration} resolution: pure map overlay of the named OIDC
 * providers ({@code providers.oidc.<id>.*}), no domain hooks. The merge itself is the generic
 * {@link PhysicalTenantMapOverlay}, registered via {@link #SPEC} — without it a tenant overriding
 * only some fields of a shared provider (e.g. {@code client-id}) would silently lose root fields
 * like {@code issuer-uri} to Spring's entry-level map replacement.
 *
 * <p>The spec deliberately roots at {@code providers} rather than the whole {@code
 * security.authentication}: only the named-provider map suffers the {@code MapBinder} defect, so
 * the sibling authentication fields (the flat {@code oidc} slot, {@code method}, …) stay on the
 * resolver's generic two-bind and the installer replaces just the providers subtree.
 */
@NullMarked
public final class PhysicalTenantAuthenticationProviderConfigurations {

  static final MapOverlaySpec<OidcProvidersConfiguration> SPEC =
      new MapOverlaySpec<>(
          "security.authentication.providers",
          OidcProvidersConfiguration.class,
          OidcProvidersConfiguration::new,
          List.of(
              new MapDescriptor<OidcProvidersConfiguration, OidcConfiguration>(
                  "oidc", OidcConfiguration.class, OidcProvidersConfiguration::getOidc)),
          MapOverlaySpec.noHook(),
          MapOverlaySpec.noHook(),
          (camunda, providers) ->
              camunda.getSecurity().getAuthentication().setProviders(providers));

  private PhysicalTenantAuthenticationProviderConfigurations() {}

  public static OidcProvidersConfiguration forPhysicalTenant(
      final String tenantId, final Environment environment) {
    return PhysicalTenantMapOverlay.overlay(SPEC, tenantId, environment);
  }
}
