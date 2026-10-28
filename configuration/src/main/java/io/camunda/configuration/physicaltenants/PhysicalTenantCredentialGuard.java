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
import io.camunda.configuration.Credential;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.security.api.model.config.KeystoreConfiguration;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Rejects a physical tenant configuration that holds a credential in plain text, before it is
 * applied: a configuration that is applied reaches the cluster state, the logs and the backups.
 *
 * <p>A field marked {@link Credential} must be empty or a {@code camunda.secrets.<name>} reference,
 * and a reference needs an internal-config secret store to resolve from. Every other value is
 * scanned for a credential that a free-form value can carry: a URL with a password in its userinfo,
 * a {@code password=} pair of a connection string, and a PEM private key.
 *
 * <p>Pass the configuration bound from the tenant's own input alone, not the configuration merged
 * with the root: a literal that the tenant inherits from the static configuration is allowed. An
 * error names the tenant and the property, never the value.
 */
@NullMarked
public final class PhysicalTenantCredentialGuard {

  /** Credential fields of classes this module cannot annotate. */
  static final Map<Class<?>, Set<String>> CREDENTIALS_OF_OTHER_MODULES =
      Map.of(
          OidcConfiguration.class, Set.of("clientSecret"),
          KeystoreConfiguration.class, Set.of("password", "keyPassword"),
          ConfiguredUser.class, Set.of("password"),
          ProxyConfiguration.class, Set.of("password"));

  private static final Pattern URL_WITH_PASSWORD =
      Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#@\\s:]*:[^/?#@\\s]+@");
  private static final Pattern CONNECTION_STRING_PASSWORD =
      Pattern.compile("(?i)(?:^|[;&?\\s])(?:password|pwd|accountkey|sharedaccesskey)=[^;&\\s]");
  private static final Pattern PEM_PRIVATE_KEY =
      Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----");

  private PhysicalTenantCredentialGuard() {}

  /**
   * @param tenantConfig the configuration bound from the tenant's own input alone
   * @param hasInternalConfigStore whether the tenant's effective configuration has an
   *     internal-config secret store, see {@link
   *     io.camunda.configuration.Secrets.Stores#hasInternalConfig()}
   * @throws UnifiedConfigurationException naming the tenant and each offending property
   */
  public static void validate(
      final String physicalTenantId,
      final Camunda tenantConfig,
      final boolean hasInternalConfigStore) {
    final var walk = new Walk(hasInternalConfigStore);
    walk.visit(tenantConfig, "", null);
    if (!walk.violations.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenant '%s' must not hold a credential in its configuration; put the value in its internal-config secret store and reference it as camunda.secrets.<name>: %s"
              .formatted(physicalTenantId, walk.violations));
    }
  }

  static boolean isCredential(final Field field) {
    return field.isAnnotationPresent(Credential.class)
        || CREDENTIALS_OF_OTHER_MODULES
            .getOrDefault(field.getDeclaringClass(), Set.of())
            .contains(field.getName());
  }

  static String propertyName(final String fieldName) {
    return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
  }

  private static final class Walk {

    private final boolean hasInternalConfigStore;
    private final List<String> violations = new ArrayList<>();
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    private Walk(final boolean hasInternalConfigStore) {
      this.hasInternalConfigStore = hasInternalConfigStore;
    }

    private void visit(
        final @Nullable Object value, final String path, final @Nullable Field field) {
      switch (value) {
        case null -> {}
        case final String text -> visitText(text, path, field);
        case final char[] text -> visitText(new String(text), path, field);
        case final Optional<?> optional -> visit(optional.orElse(null), path, field);
        case final Map<?, ?> map ->
            map.forEach((key, entry) -> visit(entry, child(path, String.valueOf(key)), null));
        case final Collection<?> collection -> {
          var index = 0;
          for (final var element : collection) {
            visit(element, path + "[" + index++ + "]", null);
          }
        }
        default -> {
          if (isConfigurationObject(value) && visited.add(value)) {
            visitFields(value, path);
          }
        }
      }
    }

    private void visitText(final String text, final String path, final @Nullable Field field) {
      if (field != null && isCredential(field)) {
        if (ConfigurationSecretReference.isReference(text)) {
          if (!hasInternalConfigStore) {
            violations.add(path + " (a reference, but no internal-config secret store)");
          }
        } else if (!text.isBlank()) {
          violations.add(path);
        }
        return;
      }
      if (URL_WITH_PASSWORD.matcher(text).find()) {
        violations.add(path + " (a URL with a password)");
      } else if (CONNECTION_STRING_PASSWORD.matcher(text).find()) {
        violations.add(path + " (a password in a connection string)");
      } else if (PEM_PRIVATE_KEY.matcher(text).find()) {
        violations.add(path + " (a private key)");
      }
    }

    private void visitFields(final Object value, final String path) {
      for (Class<?> type = value.getClass(); type != Object.class; type = type.getSuperclass()) {
        for (final var field : type.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            continue;
          }
          field.setAccessible(true);
          final Object fieldValue;
          try {
            fieldValue = field.get(value);
          } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
          }
          visit(fieldValue, child(path, propertyName(field.getName())), field);
        }
      }
    }

    private static boolean isConfigurationObject(final Object value) {
      final var type = value.getClass();
      return !type.isEnum() && type.getName().startsWith("io.camunda.");
    }

    private static String child(final String path, final String name) {
      return path.isEmpty() ? name : path + "." + name;
    }
  }
}
