/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.AssertionConfiguration;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.KeystoreConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The credentials in a physical tenant's authentication configuration that may be {@code
 * camunda.secrets.<name>} references to its internal-config secret store: the client secret and the
 * {@code private_key_jwt} keystore path, password and key password, of the default slot and of
 * every named provider. Names are relative to {@code security.authentication}, for example {@code
 * providers.oidc.idp.client-secret}.
 *
 * <p>These are exactly the properties the security library treats as credentials, apart from the
 * key alias: changing only them rotates a tenant's credentials and keeps its sessions.
 */
@NullMarked
public final class PhysicalTenantCredentials {

  public static final String CLIENT_SECRET = "client-secret";
  public static final String KEYSTORE_PATH = "assertion.keystore.path";
  public static final String KEYSTORE_PASSWORD = "assertion.keystore.password";
  public static final String KEYSTORE_KEY_PASSWORD = "assertion.keystore.key-password";

  private static final String DEFAULT_SLOT = "oidc.";
  private static final String NAMED_PROVIDER = "providers.oidc.";

  private PhysicalTenantCredentials() {}

  /** The configured value of each credential that is set, each a literal or a reference. */
  public static Map<String, String> of(final AuthenticationConfiguration authentication) {
    final Map<String, String> credentials = new LinkedHashMap<>();
    forEachProvider(
        authentication,
        (prefix, provider) -> {
          put(credentials, prefix + CLIENT_SECRET, provider.getClientSecret());
          final var keystore = keystore(provider);
          if (keystore != null) {
            put(credentials, prefix + KEYSTORE_PATH, keystore.getPath());
            put(credentials, prefix + KEYSTORE_PASSWORD, keystore.getPassword());
            put(credentials, prefix + KEYSTORE_KEY_PASSWORD, keystore.getKeyPassword());
          }
        });
    return credentials;
  }

  /** The keystore paths among the given credentials. */
  public static Set<String> keystorePaths(final Map<String, String> credentials) {
    return credentials.entrySet().stream()
        .filter(credential -> credential.getKey().endsWith(KEYSTORE_PATH))
        .map(Map.Entry::getValue)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Sets the given credential values on a configuration built for this change alone.
   *
   * @return {@code authentication}
   */
  public static AuthenticationConfiguration withValues(
      final AuthenticationConfiguration authentication, final Map<String, String> values) {
    forEachProvider(
        authentication,
        (prefix, provider) ->
            values.forEach(
                (name, value) -> {
                  if (!name.startsWith(prefix)) {
                    return;
                  }
                  switch (name.substring(prefix.length())) {
                    case CLIENT_SECRET -> provider.setClientSecret(value);
                    case KEYSTORE_PATH -> requireKeystore(provider).setPath(value);
                    case KEYSTORE_PASSWORD -> requireKeystore(provider).setPassword(value);
                    case KEYSTORE_KEY_PASSWORD -> requireKeystore(provider).setKeyPassword(value);
                    default -> {}
                  }
                }));
    return authentication;
  }

  private static void forEachProvider(
      final AuthenticationConfiguration authentication,
      final BiConsumer<String, OidcConfiguration> action) {
    if (authentication.getOidc() != null) {
      action.accept(DEFAULT_SLOT, authentication.getOidc());
    }
    if (authentication.getProviders() != null && authentication.getProviders().getOidc() != null) {
      authentication
          .getProviders()
          .getOidc()
          .forEach((id, provider) -> action.accept(NAMED_PROVIDER + id + ".", provider));
    }
  }

  private static @Nullable KeystoreConfiguration keystore(final OidcConfiguration provider) {
    return provider.getAssertion() == null ? null : provider.getAssertion().getKeystore();
  }

  private static KeystoreConfiguration requireKeystore(final OidcConfiguration provider) {
    if (provider.getAssertion() == null) {
      provider.setAssertion(new AssertionConfiguration());
    }
    if (provider.getAssertion().getKeystore() == null) {
      provider.getAssertion().setKeystore(new KeystoreConfiguration());
    }
    return provider.getAssertion().getKeystore();
  }

  private static void put(
      final Map<String, String> credentials, final String name, final @Nullable String value) {
    if (value != null && !value.isBlank()) {
      credentials.put(name, value);
    }
  }
}
