/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import io.camunda.secretstore.SecretStoreUnavailableException;
import io.github.jopenlibs.vault.EnvironmentLoader;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;

/**
 * Builds token-bound {@link Vault} clients over one shared {@link HttpClient}.
 *
 * <p>The driver keeps the token in its (mutable) {@link VaultConfig}, so a new sign-in builds a
 * new, immutable-by-convention client instead of mutating the one other threads are reading
 * through. The {@link HttpClient} — connection pool, TLS context — is shared by all of them; the
 * driver would otherwise build one per request.
 *
 * <p>The driver's default {@link EnvironmentLoader} silently falls back to {@code VAULT_ADDR},
 * {@code VAULT_TOKEN} or {@code ~/.vault-token}. That would let a store sign in as whoever last ran
 * {@code vault login} on the host, so it is replaced by one that reads nothing.
 */
final class HashiCorpVaultClients implements AutoCloseable {

  private static final EnvironmentLoader NO_ENVIRONMENT =
      new EnvironmentLoader() {
        @Override
        public @Nullable String loadVariable(final String name) {
          return null;
        }
      };

  private final HashiCorpVaultStoreConfig config;
  private final HttpClient httpClient;
  private final String address;
  private final int readTimeoutSeconds;

  HashiCorpVaultClients(final HashiCorpVaultStoreConfig config) {
    this.config = config;
    final var raw = config.address().toString();
    address = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    // the driver only takes whole seconds; round up so a sub-second timeout is not disabled
    readTimeoutSeconds = (int) Math.max(1, (config.callTimeout().toMillis() + 999) / 1000);
    final var builder =
        HttpClient.newBuilder()
            // the driver pins HTTP/1.1 too: Vault Agent fails proxying h2c to https
            .version(Version.HTTP_1_1)
            .connectTimeout(config.callTimeout());
    if (config.caBundlePath() != null) {
      builder.sslContext(sslContext(config.caBundlePath()));
    }
    httpClient = builder.build();
  }

  /**
   * @param token the token to authenticate with, or {@code null} for a client that can only sign in
   */
  Vault create(final @Nullable String token) {
    try {
      final var vaultConfig =
          new VaultConfig()
              .environmentLoader(NO_ENVIRONMENT)
              .address(address)
              .prefixPath(config.mount())
              .readTimeout(readTimeoutSeconds)
              .httpClient(httpClient);
      if (token != null) {
        vaultConfig.token(token);
      }
      if (config.namespace() != null) {
        vaultConfig.nameSpace(config.namespace());
      }
      // the driver's own retry loop sleeps on the calling thread; it defaults to no retries, and
      // the engine's scheduler owns retry/backoff for an unavailable store
      return Vault.create(vaultConfig.build(), config.kvVersion());
    } catch (final VaultException e) {
      throw new SecretStoreUnavailableException(
          "Failed to configure HashiCorp Vault client: " + e.getMessage(), e);
    }
  }

  /** The full driver path of an entry: mount, then the path inside it. */
  String fullPath(final String entryPath) {
    return config.mount() + "/" + entryPath;
  }

  @Override
  public void close() {
    httpClient.close();
  }

  /** Trusts exactly the certificates in the PEM bundle, instead of the JDK's default CAs. */
  private static SSLContext sslContext(final String caBundlePath) {
    try (final var in = Files.newInputStream(Path.of(caBundlePath))) {
      final var certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
      if (certificates.isEmpty()) {
        throw new IllegalArgumentException(
            "HashiCorp Vault CA bundle '" + caBundlePath + "' contains no certificate");
      }
      final var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      var index = 0;
      for (final var certificate : certificates) {
        trustStore.setCertificateEntry("ca-" + index++, certificate);
      }
      final var trustManagers =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagers.init(trustStore);
      final var context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers.getTrustManagers(), null);
      return context;
    } catch (final IOException | GeneralSecurityException e) {
      throw new IllegalArgumentException(
          "Failed to load HashiCorp Vault CA bundle from '" + caBundlePath + "'", e);
    }
  }
}
