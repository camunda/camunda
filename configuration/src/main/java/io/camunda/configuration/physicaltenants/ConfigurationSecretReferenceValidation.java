/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.configuration.ConfigurationValues;
import io.camunda.configuration.SecretReferenceProperties;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects a secret reference that is malformed, is in a property that doesn't accept one, or
 * belongs to a tenant without a configuration secrets store.
 */
@NullMarked
final class ConfigurationSecretReferenceValidation {

  private ConfigurationSecretReferenceValidation() {}

  static void validate(final String physicalTenantId, final Camunda camunda) {
    final var hasStore = camunda.getSecrets().getInternalConfig().isConfigured();
    final List<String> problems = new ArrayList<>();
    ConfigurationValues.walk(
        camunda,
        (property, field, value) -> {
          if (!ConfigurationSecretReference.isReference(value)
              || !PhysicalTenantResolver.isReadFromTenant(physicalTenantId, property)) {
            return value;
          }
          if (ConfigurationSecretReference.parse(value).isEmpty()) {
            problems.add(property + " holds a malformed reference");
          } else if (field == null || !SecretReferenceProperties.acceptsReference(field)) {
            problems.add(property + " doesn't accept a reference");
          } else if (!hasStore) {
            problems.add(
                property + " holds a reference, but no configuration secrets store is set");
          }
          return value;
        });
    if (!problems.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenant '%s' has invalid secret references: %s. A reference is written as %s<name> and resolved from the store under secrets.internal-config"
              .formatted(
                  physicalTenantId,
                  String.join("; ", problems),
                  ConfigurationSecretReference.PREFIX));
    }
  }
}
