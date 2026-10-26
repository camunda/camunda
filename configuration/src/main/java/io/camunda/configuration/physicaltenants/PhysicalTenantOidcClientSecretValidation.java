/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.spring.utils.InvalidPhysicalTenantIdException;
import io.camunda.spring.utils.PhysicalTenantConfigUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.Environment;

/**
 * Stops startup when a physical tenant would send the cluster OIDC client secret to a different
 * identity provider.
 *
 * <p>A tenant inherits each provider field that it does not set. If it changes the issuer or the
 * token endpoint but not the secret, the inherited secret goes to the other identity provider. A
 * secret that the tenant sets itself is accepted, because then the operator made the decision.
 */
@NullMarked
final class PhysicalTenantOidcClientSecretValidation {

  private static final String ROOT_AUTH = Camunda.PREFIX + ".security.authentication";
  private static final String TENANT_AUTH =
      PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + ".%s.security.authentication";

  private PhysicalTenantOidcClientSecretValidation() {}

  static void validate(final Environment environment) {
    final Binder binder = Binder.get(environment);
    final boolean oidcMethod =
        binder
            .bind(ROOT_AUTH + ".method", Bindable.of(String.class))
            .map("oidc"::equalsIgnoreCase)
            .orElse(false);
    if (!oidcMethod) {
      return;
    }

    final Set<String> tenantIds;
    try {
      tenantIds = PhysicalTenantConfigUtil.discover(environment);
    } catch (final InvalidPhysicalTenantIdException e) {
      throw new UnifiedConfigurationException(e);
    }

    final Set<String> sharedProviders = new LinkedHashSet<>();
    sharedProviders.add(OidcConfiguration.DEFAULT_REGISTRATION_ID);
    binder
        .bind(ROOT_AUTH + ".providers.oidc", Bindable.mapOf(String.class, OidcConfiguration.class))
        .ifBound(named -> sharedProviders.addAll(named.keySet()));

    final List<String> violations = new ArrayList<>();
    for (final String tenantId : tenantIds) {
      final String tenantAuth = TENANT_AUTH.formatted(tenantId);
      final @Nullable List<String> assigned =
          binder
              .bind(tenantAuth + ".providers.assigned", Bindable.listOf(String.class))
              .orElse(null);
      sharedProviders.forEach(
          providerId -> {
            if (assigned != null && !assigned.contains(providerId)) {
              return;
            }
            if (inheritsSecretForOtherIdp(
                binder,
                providerNames(environment, ROOT_AUTH, tenantAuth, providerId),
                providerNames(environment, tenantAuth, ROOT_AUTH, providerId))) {
              violations.add(
                  "physical tenant '%s', provider '%s': set '%s.%s.client-secret'"
                      .formatted(
                          tenantId,
                          providerId,
                          tenantAuth,
                          OidcConfiguration.DEFAULT_REGISTRATION_ID.equals(providerId)
                              ? "oidc"
                              : "providers.oidc." + providerId));
            }
          });
    }

    if (!violations.isEmpty()) {
      throw new UnifiedConfigurationException(
          "A physical tenant that changes the 'issuer-uri' or 'token-uri' of a cluster OIDC "
              + "provider must set its own 'client-secret'. If not, the cluster secret goes to the "
              + "other identity provider. "
              + violations);
    }
  }

  private static boolean inheritsSecretForOtherIdp(
      final Binder binder,
      final List<ConfigurationPropertyName> rootNames,
      final List<ConfigurationPropertyName> tenantNames) {
    final OidcConfiguration root = bindProvider(binder, rootNames, new OidcConfiguration());
    if (root.getClientSecret() == null || root.getClientSecret().isBlank()) {
      return false;
    }
    // A blank value, for example from an unset environment variable, is not a decision.
    if (tenantNames.stream()
        .anyMatch(
            name ->
                binder
                    .bind(name.append("client-secret"), Bindable.of(String.class))
                    .map(secret -> !secret.isBlank())
                    .orElse(false))) {
      return false;
    }
    // Bind in the same order as the runtime merge, so that the check sees the same values.
    final OidcConfiguration merged =
        bindProvider(binder, tenantNames, bindProvider(binder, rootNames, new OidcConfiguration()));
    // private_key_jwt does not send the secret.
    if (OidcConfiguration.CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT.equals(
        merged.getClientAuthenticationMethod())) {
      return false;
    }
    return !Objects.equals(root.getIssuerUri(), merged.getIssuerUri())
        || !Objects.equals(root.getTokenUri(), merged.getTokenUri());
  }

  private static OidcConfiguration bindProvider(
      final Binder binder,
      final List<ConfigurationPropertyName> names,
      final OidcConfiguration target) {
    names.forEach(name -> binder.bind(name, Bindable.ofInstance(target)));
    return target;
  }

  /**
   * The property names of {@code providerId}'s config under {@code authPrefix}: the default slot
   * for the reserved id, otherwise the named provider entry in each form it is declared in under
   * {@code authPrefix} or {@code relatedAuthPrefix}.
   */
  private static List<ConfigurationPropertyName> providerNames(
      final Environment environment,
      final String authPrefix,
      final String relatedAuthPrefix,
      final String providerId) {
    return OidcConfiguration.DEFAULT_REGISTRATION_ID.equals(providerId)
        ? List.of(ConfigurationPropertyName.of(authPrefix + ".oidc"))
        : PhysicalTenantConfigUtil.mapEntryNames(
            environment,
            authPrefix + ".providers.oidc",
            providerId,
            relatedAuthPrefix + ".providers.oidc");
  }
}
