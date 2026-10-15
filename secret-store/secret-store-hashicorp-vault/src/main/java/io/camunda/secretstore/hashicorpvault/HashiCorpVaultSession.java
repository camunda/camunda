/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.secretstore.hashicorpvault.HashiCorpVaultStoreConfig.AuthMethod;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the HashiCorp Vault token of one store: signs in, renews the token before it expires, and
 * signs in again when it can no longer be renewed (it reached its {@code max_ttl}, or it was
 * revoked).
 *
 * <p>The driver has no lifecycle management of its own, so this class is the renewer. A single
 * daemon thread per store does the background work; resolution threads only ever read the current
 * client, and take the lock only when a call was rejected with 403 and they need a fresh token
 * ({@link #relogin(Vault)}).
 *
 * <p>Credentials are read from their file or environment variable on every sign-in, so a rotated
 * {@code secret_id} file or projected service account token is picked up without a restart.
 */
final class HashiCorpVaultSession implements AutoCloseable {

  static final String OPERATIONS_METRIC = "camunda.secret.store.hashicorp.vault.session";

  private static final Logger LOG = LoggerFactory.getLogger(HashiCorpVaultSession.class);
  private static final Duration MIN_RETRY_DELAY = Duration.ofSeconds(1);
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

  private final HashiCorpVaultStoreConfig config;
  private final HashiCorpVaultClients clients;
  private final String storeName;
  private final Function<String, @Nullable String> environment;
  private final ScheduledExecutorService scheduler;
  private final AtomicReference<@Nullable Vault> current = new AtomicReference<>();

  private final Counter loginSuccess;
  private final Counter loginFailure;
  private final Counter renewSuccess;
  private final Counter renewFailure;

  // guarded by this
  private long loginTtlSeconds;
  private Duration retryDelay = MIN_RETRY_DELAY;
  private @Nullable ScheduledFuture<?> scheduled;
  private boolean closed;

  HashiCorpVaultSession(
      final HashiCorpVaultStoreConfig config,
      final HashiCorpVaultClients clients,
      final String storeName,
      final MeterRegistry meterRegistry,
      final Tags tags,
      final Function<String, @Nullable String> environment) {
    this.config = config;
    this.clients = clients;
    this.storeName = storeName;
    this.environment = environment;
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "hashicorp-vault-session-" + storeName);
              thread.setDaemon(true);
              return thread;
            });
    loginSuccess = counter(meterRegistry, tags, "login", "success");
    loginFailure = counter(meterRegistry, tags, "login", "failure");
    renewSuccess = counter(meterRegistry, tags, "renew", "success");
    renewFailure = counter(meterRegistry, tags, "renew", "failure");
  }

  /**
   * @return a client holding a valid token, signing in first if there is none yet
   * @throws SecretStoreUnavailableException if signing in fails
   */
  Vault vault() {
    final var vault = current.get();
    return vault != null ? vault : relogin(null);
  }

  /**
   * Signs in again, unless another thread already replaced {@code stale} with a newer client.
   *
   * @param stale the client a call was just rejected with, or {@code null} if there was none
   * @return a client holding a fresh token
   * @throws SecretStoreUnavailableException if signing in fails
   */
  synchronized Vault relogin(final @Nullable Vault stale) {
    if (closed) {
      throw new SecretStoreUnavailableException(
          "HashiCorp Vault secret store '" + storeName + "' is closed");
    }
    final var vault = current.get();
    if (vault != null && vault != stale) {
      return vault;
    }
    try {
      return signIn();
    } catch (final SecretStoreUnavailableException e) {
      scheduleRetry();
      throw e;
    }
  }

  @Override
  public void close() {
    final Vault vault;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      cancelScheduled();
      vault = current.getAndSet(null);
    }
    scheduler.shutdownNow();
    // a static token was handed to us and may be shared; only revoke what this store created
    if (vault != null && config.authMethod() != AuthMethod.TOKEN) {
      try {
        vault.auth().revokeSelf();
      } catch (final VaultException e) {
        LOG.debug(
            "Failed to revoke HashiCorp Vault token of secret store '{}' on close; it expires on"
                + " its own",
            storeName,
            e);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // sign-in and renewal; all called while holding the lock
  // ---------------------------------------------------------------------------------------------

  private Vault signIn() {
    final Vault vault;
    final long ttl;
    final boolean renewable;
    try {
      switch (config.authMethod()) {
        case APPROLE -> {
          final var response =
              clients
                  .create(null)
                  .auth()
                  .loginByAppRole(
                      config.authMount(),
                      config.role(),
                      readCredential("secret_id", config.secretIdFile(), config.secretIdEnv()));
          vault = clients.create(response.getAuthClientToken());
          ttl = response.getAuthLeaseDuration();
          renewable = response.isAuthRenewable();
        }
        case KUBERNETES -> {
          // unlike AppRole, the driver takes the full auth path here, not the mount
          final var response =
              clients
                  .create(null)
                  .auth()
                  .loginByKubernetes(
                      config.role(),
                      readCredential("service account JWT", config.jwtPath(), null),
                      "auth/" + config.authMount());
          vault = clients.create(response.getAuthClientToken());
          ttl = response.getAuthLeaseDuration();
          renewable = response.isAuthRenewable();
        }
        case TOKEN -> {
          vault = clients.create(readCredential("token", config.tokenFile(), config.tokenEnv()));
          final var lookup = vault.auth().lookupSelf(config.authMount());
          ttl = lookup.getTTL();
          renewable = lookup.isRenewable();
        }
        default -> throw new IllegalStateException("Unexpected auth method");
      }
    } catch (final VaultException e) {
      loginFailure.increment();
      final var status = e.getHttpStatusCode();
      LOG.warn(
          "Failed to sign in to HashiCorp Vault for secret store '{}' with {} auth: {}",
          storeName,
          config.authMethod(),
          status == 0 ? e.getMessage() : "HTTP " + status);
      throw HashiCorpVaultErrors.storeUnavailable(e);
    } catch (final SecretStoreUnavailableException e) {
      loginFailure.increment();
      LOG.warn(
          "Failed to sign in to HashiCorp Vault for secret store '{}': {}",
          storeName,
          e.getMessage());
      throw e;
    }

    loginSuccess.increment();
    current.set(vault);
    loginTtlSeconds = ttl;
    retryDelay = MIN_RETRY_DELAY;
    LOG.debug(
        "Signed in to HashiCorp Vault for secret store '{}' (ttl={}s, renewable={})",
        storeName,
        ttl,
        renewable);
    scheduleRenewal(ttl, renewable);
    return vault;
  }

  private synchronized void renew() {
    if (closed) {
      return;
    }
    final var vault = current.get();
    if (vault == null) {
      signInInBackground();
      return;
    }
    try {
      final var response = vault.auth().renewSelf();
      renewSuccess.increment();
      final var ttl = response.getAuthLeaseDuration();
      retryDelay = MIN_RETRY_DELAY;
      // the token is approaching its max_ttl: each renewal grants less, so sign in again rather
      // than renewing ever more often until it expires
      if (ttl * 2 < loginTtlSeconds && config.authMethod() != AuthMethod.TOKEN) {
        LOG.debug(
            "HashiCorp Vault token of secret store '{}' renewed for only {}s of {}s; signing in"
                + " again",
            storeName,
            ttl,
            loginTtlSeconds);
        signInInBackground();
        return;
      }
      LOG.debug("Renewed HashiCorp Vault token of secret store '{}' (ttl={}s)", storeName, ttl);
      scheduleRenewal(ttl, response.isAuthRenewable());
    } catch (final VaultException e) {
      renewFailure.increment();
      final var status = e.getHttpStatusCode();
      LOG.warn(
          "Failed to renew HashiCorp Vault token of secret store '{}': {}; signing in again",
          storeName,
          status == 0 ? e.getMessage() : "HTTP " + status);
      signInInBackground();
    }
  }

  private void signInInBackground() {
    try {
      signIn();
    } catch (final SecretStoreUnavailableException e) {
      // already logged and counted by signIn
      scheduleRetry();
    }
  }

  private void scheduleRenewal(final long ttlSeconds, final boolean renewable) {
    if (ttlSeconds <= 0) {
      // a non-expiring token (e.g. a root or periodic-less static token): nothing to renew
      cancelScheduled();
      return;
    }
    final var delay = Math.max(1, ttlSeconds * 2 / 3);
    schedule(renewable ? this::renew : this::signInFromScheduler, Duration.ofSeconds(delay));
  }

  private void scheduleRetry() {
    if (closed) {
      return;
    }
    schedule(this::signInFromScheduler, retryDelay);
    final var doubled = retryDelay.multipliedBy(2);
    retryDelay = doubled.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : doubled;
  }

  private synchronized void signInFromScheduler() {
    if (!closed) {
      signInInBackground();
    }
  }

  private void schedule(final Runnable task, final Duration delay) {
    cancelScheduled();
    scheduled = scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void cancelScheduled() {
    if (scheduled != null) {
      scheduled.cancel(false);
      scheduled = null;
    }
  }

  private String readCredential(
      final String what, final @Nullable String file, final @Nullable String envVariable) {
    if (file != null && !file.isBlank()) {
      try {
        final var value = Files.readString(Path.of(file)).strip();
        if (value.isEmpty()) {
          throw new SecretStoreUnavailableException(
              "HashiCorp Vault " + what + " file '" + file + "' is empty");
        }
        return value;
      } catch (final IOException e) {
        throw new SecretStoreUnavailableException(
            "Failed to read HashiCorp Vault " + what + " from file '" + file + "'", e);
      }
    }
    if (envVariable != null && !envVariable.isBlank()) {
      final var value = environment.apply(envVariable);
      if (value == null || value.isBlank()) {
        throw new SecretStoreUnavailableException(
            "HashiCorp Vault " + what + " environment variable '" + envVariable + "' is not set");
      }
      return value.strip();
    }
    throw new IllegalStateException("No source configured for HashiCorp Vault " + what);
  }

  private static Counter counter(
      final MeterRegistry registry, final Tags tags, final String operation, final String result) {
    return Counter.builder(OPERATIONS_METRIC)
        .description("HashiCorp Vault sign-in and token renewal attempts")
        .tags(tags)
        .tag("operation", operation)
        .tag("result", result)
        .register(registry);
  }
}
