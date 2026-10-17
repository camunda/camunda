/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
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
 *
 * <p>Note: {@code io.camunda.authentication.pt.PhysicalTenantAuthConfigurations} in the
 * authentication module solves the same provider merge for its own {@code
 * AuthenticationConfiguration} tree with a hand-rolled variant of this strategy (rooted at {@code
 * .authentication}, empty-map case handled by the post-bind union rebuild alone). The two must stay
 * behavior-equivalent; behavioral fixes to this engine likely apply there too. Harmonizing them
 * into one mechanism is tracked in <a
 * href="https://github.com/camunda/camunda/issues/56964">#56964</a>.
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
          (camunda, providers) ->
              camunda.getSecurity().getAuthentication().setProviders(providers));

  private PhysicalTenantAuthenticationProviderConfigurations() {}

  public static OidcProvidersConfiguration forPhysicalTenant(
      final String tenantId, final Environment environment) {
    return PhysicalTenantMapOverlay.overlay(SPEC, tenantId, environment);
  }

  /**
   * Narrows a tenant's providers to its {@code providers.assigned} selection (#54730), mirroring
   * {@code PhysicalTenantAuthConfigurations#narrowToAssigned} in the authentication module. CSL's
   * {@link OidcProvidersConfiguration} has no {@code assigned} field, so the list is read from the
   * environment: the reserved id {@link OidcConfiguration#DEFAULT_REGISTRATION_ID} keeps the
   * default slot (otherwise it keeps only the tenant-wide settings, see {@link #withoutProvider}),
   * every other id keeps the like-named provider. A tenant without a selection keeps every
   * provider.
   */
  static void narrowToAssigned(
      final Camunda physicalTenant, final String tenantId, final Environment environment) {
    final List<String> assigned =
        Binder.get(environment)
            .bind(
                PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX
                    + "."
                    + tenantId
                    + ".security.authentication.providers.assigned",
                Bindable.listOf(String.class))
            .orElse(null);
    if (assigned == null || assigned.isEmpty()) {
      return;
    }
    final Set<String> keep = new LinkedHashSet<>();
    assigned.stream().filter(id -> id != null && !id.isBlank()).forEach(keep::add);

    final AuthenticationConfiguration authentication =
        physicalTenant.getSecurity().getAuthentication();
    if (!keep.contains(OidcConfiguration.DEFAULT_REGISTRATION_ID)) {
      authentication.setOidc(withoutProvider(authentication.getOidc()));
    }
    final OidcProvidersConfiguration providers = authentication.getProviders();
    if (providers != null && providers.getOidc() != null) {
      providers.getOidc().keySet().removeIf(id -> !keep.contains(id));
    }
  }

  /**
   * The default slot without its provider fields. {@code groups-claim} (the tenant's groups mode),
   * {@code user-info-augmentation} and the principal claims also apply to the tenant's named
   * providers, so they are kept; without a {@code client-id} CSL's {@code flatten} does not treat
   * the slot as a provider.
   *
   * <p>Keep this equal to {@code PhysicalTenantAuthConfigurations#withoutProvider} in the
   * authentication module, so gRPC and REST read the same claims for a tenant.
   */
  private static OidcConfiguration withoutProvider(final OidcConfiguration defaultSlot) {
    final OidcConfiguration tenantSettings = new OidcConfiguration();
    tenantSettings.setGroupsClaim(defaultSlot.getGroupsClaim());
    tenantSettings.setUserInfoAugmentation(defaultSlot.getUserInfoAugmentation());
    tenantSettings.setUsernameClaim(defaultSlot.getUsernameClaim());
    tenantSettings.setClientIdClaim(defaultSlot.getClientIdClaim());
    tenantSettings.setPreferUsernameClaim(defaultSlot.isPreferUsernameClaim());
    return tenantSettings;
  }
}
