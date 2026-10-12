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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Fail-fast startup validation: a physical tenant that points a cluster OIDC provider at a
 * different identity provider must set its own {@code client-secret}.
 *
 * <p>A tenant inherits every provider field it does not set from the cluster provider with the same
 * id (the default slot {@code oidc} or a named {@code providers.oidc.<id>}). If the tenant changes
 * the {@code issuer-uri} or the {@code token-uri} but not the secret, the cluster secret is sent to
 * the token endpoint of the other identity provider. A secret the tenant sets itself, also one
 * equal to the cluster secret, is accepted. Providers that authenticate with {@code
 * private_key_jwt} send no secret and are not checked.
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

    // provider id -> relative path of the provider below the authentication prefix
    final Map<String, String> sharedProviders = new LinkedHashMap<>();
    sharedProviders.put(OidcConfiguration.DEFAULT_REGISTRATION_ID, "oidc");
    binder
        .bind(ROOT_AUTH + ".providers.oidc", Bindable.mapOf(String.class, OidcConfiguration.class))
        .ifBound(
            named -> named.keySet().forEach(id -> sharedProviders.put(id, "providers.oidc." + id)));

    final List<String> violations = new ArrayList<>();
    for (final String tenantId : tenantIds) {
      final String tenantAuth = TENANT_AUTH.formatted(tenantId);
      final @Nullable List<String> assigned =
          binder
              .bind(tenantAuth + ".providers.assigned", Bindable.listOf(String.class))
              .orElse(null);
      sharedProviders.forEach(
          (providerId, path) -> {
            if (assigned != null && !assigned.contains(providerId)) {
              return;
            }
            final String tenantPath = tenantAuth + "." + path;
            if (inheritsSecretForOtherIdp(binder, ROOT_AUTH + "." + path, tenantPath)) {
              violations.add(
                  "physical tenant '%s', provider '%s': set '%s.client-secret'"
                      .formatted(tenantId, providerId, tenantPath));
            }
          });
    }

    if (!violations.isEmpty()) {
      throw new UnifiedConfigurationException(
          "A physical tenant that changes the 'issuer-uri' or 'token-uri' of a cluster OIDC "
              + "provider must set its own 'client-secret'; otherwise the cluster secret is sent to "
              + "the other identity provider. "
              + violations);
    }
  }

  private static boolean inheritsSecretForOtherIdp(
      final Binder binder, final String rootPath, final String tenantPath) {
    final OidcConfiguration root = bindProvider(binder, rootPath, new OidcConfiguration());
    if (root.getClientSecret() == null || root.getClientSecret().isBlank()) {
      return false;
    }
    if (binder.bind(tenantPath + ".client-secret", Bindable.of(String.class)).isBound()) {
      return false;
    }
    // the same binding order as the runtime merge: root first, tenant overlay on top
    final OidcConfiguration merged =
        bindProvider(binder, tenantPath, bindProvider(binder, rootPath, new OidcConfiguration()));
    if (OidcConfiguration.CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT.equals(
        merged.getClientAuthenticationMethod())) {
      return false;
    }
    return !Objects.equals(root.getIssuerUri(), merged.getIssuerUri())
        || !Objects.equals(root.getTokenUri(), merged.getTokenUri());
  }

  private static OidcConfiguration bindProvider(
      final Binder binder, final String path, final OidcConfiguration target) {
    binder.bind(path, Bindable.ofInstance(target));
    return target;
  }
}
