/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.application.commons.secrets.InternalConfigRefresher;
import io.camunda.application.commons.secrets.InternalConfigRefreshers;
import io.camunda.application.commons.secrets.PhysicalTenantSecretStores;
import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.authentication.pt.PhysicalTenantCredentials;
import io.camunda.authentication.pt.PhysicalTenantHttpSecurity;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.security.api.model.scope.ScopeReloadOutcome;
import io.camunda.security.api.model.scope.ScopeReloadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.env.Environment;

/**
 * The REST side of a physical tenant on this node: builds the tenant's authentication configuration
 * from the environment a change resolved against, exactly as the startup scope provider does,
 * resolves its credential references from the tenant's internal-config secret store, and hands it
 * to the security library through {@link PhysicalTenantHttpSecurity}. Without scoped HTTP security
 * on this node (no security library profile), it does nothing.
 *
 * <p>The tenant's client secret and {@code private_key_jwt} keystore path, password and key
 * password may be {@code camunda.secrets.<name>} references (see {@link
 * PhysicalTenantCredentials}). They are resolved when a change is applied, and the tenant's secret
 * refresher re-reads them at its interval: a changed value rotates the tenant's credentials, which
 * keeps its sessions. A keystore file changed at the same path is noticed by its content digest on
 * the same schedule and makes the library read the key material again.
 *
 * <p>It remembers the last result per tenant, for the tenant's health and its rollout status.
 */
@NullMarked
public final class PhysicalTenantRestSecurity {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final String CONSUMER_NAME = "http-security";

  private final @Nullable PhysicalTenantHttpSecurity httpSecurity;
  private final @Nullable PhysicalTenantSecretStores secretStores;
  private final @Nullable InternalConfigRefreshers refreshers;
  private final Map<String, Status> statuses = new ConcurrentHashMap<>();
  private final Map<String, Served> served = new ConcurrentHashMap<>();
  private final Map<String, InternalConfigRefresher> watchedBy = new ConcurrentHashMap<>();
  private final Map<String, Object> locks = new ConcurrentHashMap<>();

  public PhysicalTenantRestSecurity(
      final @Nullable PhysicalTenantHttpSecurity httpSecurity,
      final @Nullable PhysicalTenantSecretStores secretStores,
      final @Nullable InternalConfigRefreshers refreshers) {
    this.httpSecurity = httpSecurity;
    this.secretStores = secretStores;
    this.refreshers = refreshers;
  }

  /** For a node without scoped HTTP security. */
  public static PhysicalTenantRestSecurity none() {
    return new PhysicalTenantRestSecurity(null, null, null);
  }

  /**
   * Gives the tenants of the startup configuration their credentials: the security library built
   * their chains with the configured values, so a reference is resolved and applied here, and the
   * tenant's refresher is told to watch them. Call once every chain of the startup configuration
   * exists.
   */
  public void startWith(final Map<String, Camunda> startupTenants, final Environment environment) {
    if (httpSecurity == null) {
      return;
    }
    startupTenants.forEach(
        (physicalTenantId, configuration) -> {
          if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
            return;
          }
          try {
            apply(physicalTenantId, environment, configuration)
                .filter(result -> !PhysicalTenantHttpSecurity.takesEffect(result))
                .ifPresent(
                    result ->
                        LOG.warn(
                            "Physical tenant '{}' keeps the credentials it started with: {}",
                            physicalTenantId,
                            result.outcome()));
          } catch (final RuntimeException e) {
            LOG.warn(
                "Physical tenant '{}' keeps the credentials it started with", physicalTenantId, e);
          }
        });
  }

  /**
   * Adds or replaces the tenant's HTTP security with the configuration resolved from {@code
   * environment}, its credential references resolved from the internal-config store that {@code
   * configuration} declares.
   *
   * @return the library's result, or empty if this node has no scoped HTTP security
   * @throws RuntimeException if a credential reference cannot be resolved; nothing changed then
   */
  public Optional<ScopeReloadResult> apply(
      final String physicalTenantId, final Environment environment, final Camunda configuration) {
    if (httpSecurity == null) {
      return Optional.empty();
    }
    return Optional.of(
        locked(
            physicalTenantId,
            () -> {
              final var configured =
                  PhysicalTenantCredentials.of(
                      PhysicalTenantAuthConfigurations.forPhysicalTenant(
                          physicalTenantId, environment));
              final var resolved = resolve(physicalTenantId, configuration, configured);
              final var result =
                  httpSecurity.apply(
                      physicalTenantId, authentication(physicalTenantId, environment, resolved));
              record(physicalTenantId, result);
              if (PhysicalTenantHttpSecurity.takesEffect(result)) {
                served.put(
                    physicalTenantId,
                    new Served(environment, configured, resolved, keystoreDigests(resolved)));
                watch(physicalTenantId);
              }
              return result;
            }));
  }

  /** Forgets what a change that was not made asked for. */
  public void revert(final String physicalTenantId) {
    if (httpSecurity != null) {
      httpSecurity.revert(physicalTenantId);
    }
  }

  /** Removes the tenant's HTTP security, so that its whole base path answers 404. */
  public void remove(final String physicalTenantId) {
    locked(
        physicalTenantId,
        () -> {
          if (httpSecurity != null) {
            httpSecurity.remove(physicalTenantId);
          }
          served.remove(physicalTenantId);
          watchedBy.remove(physicalTenantId);
          statuses.remove(physicalTenantId);
          return null;
        });
  }

  /** The result of the last change of the tenant's HTTP security on this node. */
  public Optional<Status> status(final String physicalTenantId) {
    return Optional.ofNullable(statuses.get(physicalTenantId));
  }

  /**
   * Makes the tenant's secret refresher watch its credentials, once the refresher exists: an added
   * tenant gets it before its module starts, and a replaced tenant keeps it.
   */
  private void watch(final String physicalTenantId) {
    if (refreshers == null) {
      return;
    }
    refreshers
        .forPhysicalTenant(physicalTenantId)
        .filter(refresher -> !refresher.isClosed())
        .filter(refresher -> watchedBy.get(physicalTenantId) != refresher)
        .ifPresent(
            refresher -> {
              final var current = served.get(physicalTenantId);
              refresher.register(
                  new Credentials(physicalTenantId),
                  current == null ? Map.of() : current.resolved());
              watchedBy.put(physicalTenantId, refresher);
            });
  }

  private void rotate(final String physicalTenantId, final Map<String, String> resolved) {
    locked(
        physicalTenantId,
        () -> {
          final var current = served.get(physicalTenantId);
          if (current == null || resolved.equals(current.resolved())) {
            return null;
          }
          final var result =
              requireHttpSecurity()
                  .rotateCredentials(
                      physicalTenantId,
                      authentication(physicalTenantId, current.environment(), resolved));
          record(physicalTenantId, result);
          if (!PhysicalTenantHttpSecurity.takesEffect(result)) {
            throw notApplied(result);
          }
          served.put(
              physicalTenantId,
              new Served(
                  current.environment(),
                  current.configured(),
                  resolved,
                  keystoreDigests(resolved)));
          return null;
        });
  }

  private void refreshKeystores(final String physicalTenantId) {
    locked(
        physicalTenantId,
        () -> {
          final var current = served.get(physicalTenantId);
          if (current == null) {
            return null;
          }
          final var digests = keystoreDigests(current.resolved());
          if (digests.equals(current.keystoreDigests())) {
            return null;
          }
          final var result = requireHttpSecurity().refreshCredentials(physicalTenantId);
          record(physicalTenantId, result);
          if (!PhysicalTenantHttpSecurity.takesEffect(result)) {
            throw notApplied(result);
          }
          served.put(
              physicalTenantId,
              new Served(current.environment(), current.configured(), current.resolved(), digests));
          return null;
        });
  }

  private Map<String, String> resolve(
      final String physicalTenantId,
      final Camunda configuration,
      final Map<String, String> configured) {
    if (configured.values().stream().noneMatch(ConfigurationSecretReference::isReference)) {
      return configured;
    }
    if (secretStores == null) {
      throw new IllegalStateException(
          "Physical tenant '%s' references secrets in %s, but this node builds no secret stores"
              .formatted(physicalTenantId, configured.keySet()));
    }
    try (final var store =
        secretStores.internalConfig(physicalTenantId, configuration.getSecrets())) {
      return store.value().resolve(configured);
    }
  }

  private static io.camunda.security.api.model.config.AuthenticationConfiguration authentication(
      final String physicalTenantId,
      final Environment environment,
      final Map<String, String> credentials) {
    return PhysicalTenantCredentials.withValues(
        PhysicalTenantAuthConfigurations.forPhysicalTenant(physicalTenantId, environment),
        credentials);
  }

  private static Map<String, String> keystoreDigests(final Map<String, String> credentials) {
    final Map<String, String> digests = new TreeMap<>();
    for (final var path : PhysicalTenantCredentials.keystorePaths(credentials)) {
      try {
        digests.put(
            path,
            HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(Path.of(path)))));
      } catch (final IOException e) {
        digests.put(path, "unreadable: " + e.getClass().getSimpleName());
      } catch (final NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
    return digests;
  }

  private PhysicalTenantHttpSecurity requireHttpSecurity() {
    if (httpSecurity == null) {
      throw new IllegalStateException("This node has no scoped HTTP security");
    }
    return httpSecurity;
  }

  private void record(final String physicalTenantId, final ScopeReloadResult result) {
    statuses.put(physicalTenantId, Status.of(result));
  }

  private <T> T locked(final String physicalTenantId, final Supplier<T> change) {
    synchronized (locks.computeIfAbsent(physicalTenantId, id -> new Object())) {
      return change.get();
    }
  }

  private static IllegalStateException notApplied(final ScopeReloadResult result) {
    return new IllegalStateException(
        "the HTTP security kept its previous credentials: %s%s"
            .formatted(
                result.outcome(),
                result.failureCause().map(failure -> " (" + describe(failure) + ")").orElse("")));
  }

  static String describe(final Throwable failure) {
    var root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root == failure
        ? failure.getClass().getSimpleName() + ": " + failure.getMessage()
        : failure.getClass().getSimpleName()
            + ": "
            + failure.getMessage()
            + " (caused by "
            + root.getClass().getSimpleName()
            + ": "
            + root.getMessage()
            + ")";
  }

  /**
   * What a tenant's HTTP security serves on this node.
   *
   * @param environment the environment its configuration was built from
   * @param configured its credentials as configured, each a literal or a reference
   * @param resolved its credentials with every reference resolved
   * @param keystoreDigests the content digest of each keystore file when it was last read
   */
  private record Served(
      Environment environment,
      Map<String, String> configured,
      Map<String, String> resolved,
      Map<String, String> keystoreDigests) {}

  /** The tenant's credentials as a consumer of its secret refresher. */
  private final class Credentials implements InternalConfigRefresher.Consumer {

    private final String physicalTenantId;

    private Credentials(final String physicalTenantId) {
      this.physicalTenantId = physicalTenantId;
    }

    @Override
    public String name() {
      return CONSUMER_NAME;
    }

    @Override
    public Map<String, String> configured() {
      final var current = served.get(physicalTenantId);
      return current == null ? Map.of() : current.configured();
    }

    @Override
    public void apply(final Map<String, String> resolved) {
      rotate(physicalTenantId, resolved);
    }

    @Override
    public boolean polls() {
      return true;
    }

    @Override
    public void poll() {
      refreshKeystores(physicalTenantId);
    }
  }

  /**
   * The result of the last change of a tenant's HTTP security on this node.
   *
   * @param outcome what the security library did
   * @param applied whether the tenant's chains serve what the change asked for
   * @param generation the generation of the tenant's chains that serves now
   * @param failure why the change did not take effect; never a credential
   */
  public record Status(
      ScopeReloadOutcome outcome,
      boolean applied,
      long generation,
      long durationMillis,
      Instant at,
      @Nullable String failure) {

    static Status of(final ScopeReloadResult result) {
      return new Status(
          result.outcome(),
          PhysicalTenantHttpSecurity.takesEffect(result),
          result.generation(),
          result.duration().toMillis(),
          Instant.now(),
          result.failureCause().map(PhysicalTenantRestSecurity::describe).orElse(null));
    }
  }
}
