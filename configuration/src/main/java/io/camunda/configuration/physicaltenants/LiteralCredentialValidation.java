/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.configuration.ConfigurationValues;
import io.camunda.configuration.SecretReferenceProperties;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Reports each credential that holds a literal value instead of a secret reference, and fails
 * startup instead when {@code camunda.secrets.reject-literal-credentials} is set. A tenant is only
 * checked for its own properties, not those it inherits from the root configuration, which are
 * checked for {@code default}.
 */
@NullMarked
final class LiteralCredentialValidation {

  private static final Logger LOG = LoggerFactory.getLogger(LiteralCredentialValidation.class);

  private LiteralCredentialValidation() {}

  static void validate(
      final Environment environment,
      final Map<String, Camunda> resolved,
      final boolean rejectLiteralCredentials) {
    final var binder = Binder.get(environment);
    final List<String> literals = new ArrayList<>();
    resolved.forEach(
        (physicalTenantId, camunda) -> {
          final String prefix;
          final Camunda own;
          if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
            prefix = Camunda.PREFIX;
            own = camunda;
          } else {
            prefix = PhysicalTenantResolver.PHYSICAL_TENANTS_PREFIX + "." + physicalTenantId;
            own = binder.bind(prefix, Bindable.ofInstance(new Camunda())).orElseGet(Camunda::new);
          }
          ConfigurationValues.walk(
              own,
              (property, field, value) -> {
                if (field != null
                    && SecretReferenceProperties.isCredential(field)
                    && PhysicalTenantResolver.isReadFromTenant(physicalTenantId, property)
                    && !value.isBlank()
                    && !ConfigurationSecretReference.isReference(value)) {
                  literals.add(prefix + "." + property);
                }
                return value;
              });
        });
    if (literals.isEmpty()) {
      return;
    }
    if (rejectLiteralCredentials) {
      throw new UnifiedConfigurationException(
          "Credentials must refer to a configuration secret as %s<name>, since camunda.secrets.reject-literal-credentials is set, but these hold a literal value: %s"
              .formatted(ConfigurationSecretReference.PREFIX, String.join(", ", literals)));
    }
    literals.forEach(
        property ->
            LOG.warn(
                "{} holds a literal credential; put it in the configuration secrets store and refer to it as {}<name>",
                property,
                ConfigurationSecretReference.PREFIX));
  }
}
