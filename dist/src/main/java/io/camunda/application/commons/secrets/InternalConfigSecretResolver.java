/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.secretstore.LocallyCachedSecretStore;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the configuration values of one physical tenant: a literal stays as it is, and a {@code
 * camunda.secrets.<name>} reference is read from the tenant's internal-config store.
 *
 * <p>Every read goes to the store and never to its cache, so a value rotated in the store is seen
 * on the next call.
 */
public final class InternalConfigSecretResolver {

  private final String physicalTenantId;
  private final @Nullable LocallyCachedSecretStore store;

  InternalConfigSecretResolver(
      final String physicalTenantId, final @Nullable LocallyCachedSecretStore store) {
    this.physicalTenantId = physicalTenantId;
    this.store = store;
  }

  public String physicalTenantId() {
    return physicalTenantId;
  }

  /**
   * @param configured the configured value per property name, each a literal or a reference
   * @return the resolved value per property name, in the same order
   * @throws InternalConfigResolutionException if a reference cannot be resolved; the message names
   *     the tenant, the property and the reference, never a value
   */
  public Map<String, String> resolve(final Map<String, String> configured) {
    final Map<String, String> referenced = new LinkedHashMap<>();
    configured.forEach(
        (property, value) ->
            ConfigurationSecretReference.nameOf(value)
                .ifPresent(name -> referenced.put(property, name)));
    if (referenced.isEmpty()) {
      return configured;
    }
    if (store == null) {
      throw new InternalConfigResolutionException(
          "Physical tenant '%s' references secrets in %s, but has no internal-config secret store; configure an internal-config block in its secret store entry"
              .formatted(physicalTenantId, referenced.keySet()));
    }

    final Map<String, SecretResolutionResult> results;
    try {
      results = store.resolveFromStore(Set.copyOf(referenced.values()));
    } catch (final SecretStoreException e) {
      throw new InternalConfigResolutionException(
          "Physical tenant '%s' cannot read the secrets referenced in %s: its internal-config secret store is unavailable"
              .formatted(physicalTenantId, referenced.keySet()),
          e);
    }

    final Map<String, String> resolved = new LinkedHashMap<>(configured);
    final Map<String, String> failures = new LinkedHashMap<>();
    referenced.forEach(
        (property, name) -> {
          switch (results.get(name)) {
            case final Resolved value -> resolved.put(property, value.value());
            case final Failed failed -> failures.put(property, name + ": " + failed.code());
            case null -> failures.put(property, name + ": no result");
          }
        });
    if (!failures.isEmpty()) {
      throw new InternalConfigResolutionException(
          "Physical tenant '%s' cannot resolve the secrets referenced in %s"
              .formatted(
                  physicalTenantId,
                  failures.entrySet().stream()
                      .map(e -> e.getKey() + " (" + e.getValue() + ")")
                      .collect(Collectors.joining(", ", "[", "]"))));
    }
    return resolved;
  }
}
