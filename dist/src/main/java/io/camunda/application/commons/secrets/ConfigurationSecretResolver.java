/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.configuration.ConfigurationValues;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves the secret references of one tenant's configuration and writes the values in place. */
final class ConfigurationSecretResolver {

  private ConfigurationSecretResolver() {}

  /**
   * Replaces every reference in the given configuration with its value from the store, apart from
   * those in properties that are only read from the root configuration.
   *
   * @return the reference behind each property that held one
   * @throws IllegalStateException naming the tenant, the property and the reference (but not the
   *     value) when a reference can't be resolved
   */
  static Map<String, ConfigurationSecretReference> resolveInPlace(
      final String physicalTenantId,
      final Object configuration,
      final Optional<SecretStore> store) {
    return resolveInPlace(physicalTenantId, configuration, "", store);
  }

  /**
   * Like {@link #resolveInPlace(String, Object, Optional)}, for a configuration object bound under
   * the given property prefix (relative to {@code camunda.}), such as {@code security.}.
   */
  static Map<String, ConfigurationSecretReference> resolveInPlace(
      final String physicalTenantId,
      final Object configuration,
      final String prefix,
      final Optional<SecretStore> store) {
    final Map<String, ConfigurationSecretReference> references = new LinkedHashMap<>();
    ConfigurationValues.walk(
        configuration,
        (relative, field, value) -> {
          final var property = prefix + relative;
          if (PhysicalTenantResolver.isReadFromTenant(physicalTenantId, property)) {
            ConfigurationSecretReference.parse(value)
                .ifPresent(reference -> references.put(property, reference));
          }
          return value;
        });
    if (references.isEmpty()) {
      return references;
    }

    final var names =
        references.values().stream()
            .map(ConfigurationSecretReference::name)
            .collect(Collectors.toCollection(HashSet::new));
    final var results = read(physicalTenantId, store, names, references);
    final Map<String, String> values = new HashMap<>();
    final List<String> failures = new ArrayList<>();
    references.forEach(
        (property, reference) -> {
          switch (results.get(reference.name())) {
            case final Resolved resolved -> values.put(property, resolved.value());
            case final Failed failed ->
                failures.add("%s for %s (%s)".formatted(reference, property, failed.code()));
            case null -> failures.add("%s for %s (no result)".formatted(reference, property));
          }
        });
    if (!failures.isEmpty()) {
      throw new IllegalStateException(
          "Physical tenant '%s' can't resolve the secret references in its configuration: %s"
              .formatted(physicalTenantId, String.join("; ", failures)));
    }

    ConfigurationValues.walk(
        configuration, (relative, field, value) -> values.getOrDefault(prefix + relative, value));
    return references;
  }

  private static Map<String, SecretResolutionResult> read(
      final String physicalTenantId,
      final Optional<SecretStore> store,
      final Set<String> names,
      final Map<String, ConfigurationSecretReference> references) {
    final var configurationStore =
        store.orElseThrow(
            () ->
                new IllegalStateException(
                    "Physical tenant '%s' refers to secrets in %s, but has no configuration secrets store"
                        .formatted(physicalTenantId, references.keySet())));
    try {
      return configurationStore.resolve(names);
    } catch (final SecretStoreException e) {
      throw new IllegalStateException(
          "Physical tenant '%s' can't read its configuration secrets store to resolve %s"
              .formatted(physicalTenantId, references.keySet()),
          e);
    }
  }
}
