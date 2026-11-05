/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.spring.utils.PhysicalTenantConfigUtil;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps OIDC refresh-token revocation at logout off by default on 8.10, as it was before the
 * camunda-security-library 1.2.0 upgrade, which turns it on by default.
 *
 * <p>For every configured OIDC provider (primary and named, at the root and per physical tenant) it
 * adds {@code revocation-enabled=false} with the lowest precedence, so an operator can still opt in
 * by setting {@code revocation-enabled=true}.
 *
 * <p>A physical tenant inherits the root configuration of a provider the root also configures, so
 * such a provider only gets the root default; a tenant default would override the root opt-in.
 */
public class OidcRevocationDefaultPostProcessor implements EnvironmentPostProcessor {

  static final String NAME = "oidc-revocation-disabled-default";

  private static final String REVOCATION_ENABLED = "revocation-enabled";
  private static final ConfigurationPropertyName PHYSICAL_TENANTS =
      ConfigurationPropertyName.of("camunda.physical-tenants");
  private static final ConfigurationPropertyName TENANT_AUTHENTICATION =
      ConfigurationPropertyName.of("security.authentication");
  private static final ConfigurationPropertyName ROOT_AUTHENTICATION =
      ConfigurationPropertyName.of("camunda").append(TENANT_AUTHENTICATION);
  private static final ConfigurationPropertyName PRIMARY_PROVIDER =
      ConfigurationPropertyName.of("oidc");
  private static final ConfigurationPropertyName NAMED_PROVIDERS =
      ConfigurationPropertyName.of("providers.oidc");

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    // 1. Collect the OIDC providers configured at the root, relative to security.authentication
    //    (e.g. "oidc" or "providers.oidc.keycloak"), from every source and in every key form.
    final Set<ConfigurationPropertyName> rootProviders = new HashSet<>();
    ConfigurationPropertySources.get(environment)
        .forEach(
            source -> {
              if (source instanceof final IterableConfigurationPropertySource iterable) {
                iterable.stream()
                    .filter(ROOT_AUTHENTICATION::isAncestorOf)
                    .map(name -> name.subName(ROOT_AUTHENTICATION.getNumberOfElements()))
                    .map(OidcRevocationDefaultPostProcessor::provider)
                    .flatMap(Optional::stream)
                    .forEach(rootProviders::add);
              }
            });

    // 2. Every root provider gets the default. A physical tenant binds the root configuration
    //    first and its own on top, so it inherits the root default or the operator's opt-in.
    final Set<ConfigurationPropertyName> providers = new LinkedHashSet<>();
    rootProviders.forEach(provider -> providers.add(ROOT_AUTHENTICATION.append(provider)));
    // 3. A tenant provider gets its own default only if the root does not configure it: it has
    //    nothing to inherit, and a tenant default would override a root opt-in.
    PhysicalTenantConfigUtil.forEachTenantProperty(
        environment,
        (tenantId, name) -> {
          if (TENANT_AUTHENTICATION.isAncestorOf(name)) {
            provider(name.subName(TENANT_AUTHENTICATION.getNumberOfElements()))
                .filter(provider -> !rootProviders.contains(provider))
                .ifPresent(
                    provider ->
                        providers.add(
                            PHYSICAL_TENANTS
                                .append(tenantId)
                                .append(TENANT_AUTHENTICATION)
                                .append(provider)));
          }
        });

    // 4. Add the defaults with the lowest precedence, so any explicit revocation-enabled wins.
    //    Without OIDC no property source is added.
    if (!providers.isEmpty()) {
      final Map<String, Object> defaults = new LinkedHashMap<>();
      providers.forEach(
          provider -> defaults.put(provider.append(REVOCATION_ENABLED).toString(), "false"));
      environment.getPropertySources().addLast(new MapPropertySource(NAME, defaults));
    }
  }

  /**
   * Returns the OIDC provider a property relative to {@code security.authentication} belongs to,
   * e.g. {@code providers.oidc.keycloak} for {@code providers.oidc.keycloak.client-id}. The same
   * provider at the root and in a physical tenant gives an equal name.
   */
  private static Optional<ConfigurationPropertyName> provider(
      final ConfigurationPropertyName name) {
    if (PRIMARY_PROVIDER.isAncestorOf(name)) {
      return Optional.of(PRIMARY_PROVIDER);
    }
    final int providerId = NAMED_PROVIDERS.getNumberOfElements();
    if (NAMED_PROVIDERS.isAncestorOf(name) && name.getNumberOfElements() > providerId + 1) {
      return Optional.of(name.chop(providerId + 1));
    }
    return Optional.empty();
  }
}
