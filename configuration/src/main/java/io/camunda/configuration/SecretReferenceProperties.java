/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.search.connect.configuration.ProxyConfiguration;
import io.camunda.security.api.model.config.KeystoreConfiguration;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Identifies which configuration properties accept a secret reference, and which of those hold
 * credentials.
 */
@NullMarked
public final class SecretReferenceProperties {

  /** Credentials in classes that this module can't annotate. */
  static final Map<Class<?>, Set<String>> CREDENTIALS_OF_OTHER_MODULES =
      Map.of(
          OidcConfiguration.class, Set.of("clientSecret"),
          KeystoreConfiguration.class, Set.of("password", "keyPassword"),
          ConfiguredUser.class, Set.of("password"),
          ProxyConfiguration.class, Set.of("password"));

  /**
   * Properties in those classes that accept a reference: the credentials above, except for the OIDC
   * ones (CSL builds OIDC client registrations from its own binding of the configuration, so cannot
   * accept a reference yet), and the usernames next to them.
   */
  static final Map<Class<?>, Set<String>> REFERENCES_OF_OTHER_MODULES =
      Map.of(
          ConfiguredUser.class, Set.of("username", "password"),
          ProxyConfiguration.class, Set.of("username", "password"));

  private SecretReferenceProperties() {}

  public static boolean acceptsReference(final Field field) {
    return field.isAnnotationPresent(AcceptsSecretReference.class)
        || listed(REFERENCES_OF_OTHER_MODULES, field);
  }

  public static boolean isCredential(final Field field) {
    final var mark = field.getAnnotation(AcceptsSecretReference.class);
    return mark != null ? mark.credential() : listed(CREDENTIALS_OF_OTHER_MODULES, field);
  }

  private static boolean listed(final Map<Class<?>, Set<String>> fields, final Field field) {
    return fields.getOrDefault(field.getDeclaringClass(), Set.of()).contains(field.getName());
  }
}
