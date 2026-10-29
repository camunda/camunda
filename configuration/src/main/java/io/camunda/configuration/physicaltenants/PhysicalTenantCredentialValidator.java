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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Runs {@link PhysicalTenantCredentialGuard} on every change of a physical tenant's configuration,
 * from the management API and from the file alike, so that a change holding a literal credential is
 * rejected before the cluster stores it.
 *
 * <p>The guard sees the proposed subtree bound alone, so only what the change itself sets is
 * checked. Whether a reference can resolve depends on the tenant's effective configuration, which
 * may inherit its internal-config secret store from the root.
 */
@NullMarked
public final class PhysicalTenantCredentialValidator implements PhysicalTenantConfigValidator {

  private final BiFunction<String, Map<String, String>, Camunda> effectiveConfiguration;

  /**
   * @param effectiveConfiguration resolves the configuration a tenant would run with, the root
   *     merged with the proposed subtree
   */
  public PhysicalTenantCredentialValidator(
      final BiFunction<String, Map<String, String>, Camunda> effectiveConfiguration) {
    this.effectiveConfiguration = effectiveConfiguration;
  }

  @Override
  public List<String> validate(final PhysicalTenantConfigChange change) {
    final Camunda proposed;
    try {
      proposed = bindAlone(change.proposed());
    } catch (final RuntimeException e) {
      return List.of(
          "The configuration of physical tenant '%s' cannot be bound (%s)"
              .formatted(change.physicalTenantId(), e.getClass().getSimpleName()));
    }
    try {
      PhysicalTenantCredentialGuard.validate(
          change.physicalTenantId(), proposed, hasInternalConfigStore(change, proposed));
      return List.of();
    } catch (final UnifiedConfigurationException e) {
      return List.of(String.valueOf(e.getMessage()));
    }
  }

  private boolean hasInternalConfigStore(
      final PhysicalTenantConfigChange change, final Camunda proposed) {
    try {
      return effectiveConfiguration
          .apply(change.physicalTenantId(), change.proposed())
          .getSecrets()
          .getStores()
          .hasInternalConfig();
    } catch (final RuntimeException e) {
      // the boot validations that follow report why the configuration does not resolve
      return proposed.getSecrets().getStores().hasInternalConfig();
    }
  }

  private static Camunda bindAlone(final Map<String, String> subtree) {
    final Map<String, Object> prefixed = new LinkedHashMap<>();
    subtree.forEach((name, value) -> prefixed.put(Camunda.PREFIX + "." + name, value));
    final var environment = new AbstractEnvironment() {};
    environment.getPropertySources().addFirst(new MapPropertySource("proposed", prefixed));
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return camunda;
  }
}
