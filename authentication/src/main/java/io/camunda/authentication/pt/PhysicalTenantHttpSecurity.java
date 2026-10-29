/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.scope.ScopeReloadOutcome;
import io.camunda.security.api.model.scope.ScopeReloadResult;
import io.camunda.security.core.port.in.ScopedSecurityReloadPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Changes the HTTP security of one physical tenant on this node while it runs: its scoped API and
 * webapp chains through the security library's {@link ScopedSecurityReloadPort}, and with them the
 * host-side state built from the tenant's configuration ({@link PhysicalTenantAuthentications}).
 *
 * <p>The scope provider takes the configuration the tenant should have before the library is asked,
 * so that a reload of every scope or a later retry asks for the same. {@link
 * PhysicalTenantAuthentications} takes it only once the library serves it, so host-side state never
 * runs ahead of the chains: a change the library kept back leaves both on the previous
 * configuration.
 *
 * <p>The changes of one tenant are serialised; different tenants change in parallel. The {@code
 * default} tenant is the root configuration that the unprefixed chains are built from at startup
 * and cannot change here.
 */
@NullMarked
public final class PhysicalTenantHttpSecurity {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantHttpSecurity.class);

  private final PhysicalTenantScopeProvider scopes;
  private final Supplier<ScopedSecurityReloadPort> port;
  private final PhysicalTenantAuthentications served;
  private final Map<String, Object> locks = new ConcurrentHashMap<>();

  /**
   * @param port the security library's port, looked up when a tenant changes first: the library
   *     registers it while it builds the chains, after this is created
   */
  public PhysicalTenantHttpSecurity(
      final PhysicalTenantScopeProvider scopes,
      final Supplier<ScopedSecurityReloadPort> port,
      final PhysicalTenantAuthentications served) {
    this.scopes = scopes;
    this.port = port;
    this.served = served;
  }

  /**
   * Adds the tenant's chains, or replaces them with the given configuration. The library decides
   * whether the change ends the tenant's sessions ({@link ScopeReloadOutcome#REPLACED}) or only
   * rotates credentials ({@link ScopeReloadOutcome#CREDENTIALS_ROTATED}).
   *
   * @param authentication built for this change alone, with credential references resolved; it must
   *     not be changed afterwards
   */
  public ScopeReloadResult apply(
      final String physicalTenantId, final AuthenticationConfiguration authentication) {
    requireNotDefault(physicalTenantId);
    return locked(
        physicalTenantId,
        () -> {
          final var descriptor = scopes.put(physicalTenantId, authentication);
          return record(physicalTenantId, authentication, port.get().apply(descriptor));
        });
  }

  /**
   * Replaces the credentials of the tenant's chains, keeping its sessions.
   *
   * @throws IllegalArgumentException if the configuration changes more than credentials
   */
  public ScopeReloadResult rotateCredentials(
      final String physicalTenantId, final AuthenticationConfiguration authentication) {
    requireNotDefault(physicalTenantId);
    return locked(
        physicalTenantId,
        () -> {
          final var previous = scopes.descriptor(physicalTenantId);
          final var descriptor = scopes.put(physicalTenantId, authentication);
          try {
            return record(
                physicalTenantId, authentication, port.get().rotateCredentials(descriptor));
          } catch (final IllegalArgumentException e) {
            previous.ifPresent(before -> scopes.put(physicalTenantId, before.authentication()));
            throw e;
          }
        });
  }

  /** Reads the tenant's key material again, for a keystore replaced at the same path. */
  public ScopeReloadResult refreshCredentials(final String physicalTenantId) {
    requireNotDefault(physicalTenantId);
    return locked(
        physicalTenantId,
        () -> {
          final var result =
              port.get().refreshCredentials(PhysicalTenantScopeProvider.basePath(physicalTenantId));
          log(physicalTenantId, result);
          return result;
        });
  }

  /**
   * Puts the configuration back that the tenant should have, after a change of the tenant was not
   * made, so that a later reload of every scope does not ask for it.
   */
  public void revert(final String physicalTenantId) {
    locked(
        physicalTenantId,
        () -> {
          served
              .find(physicalTenantId)
              .ifPresentOrElse(
                  authentication -> scopes.put(physicalTenantId, authentication),
                  () -> scopes.remove(physicalTenantId));
          return null;
        });
  }

  /** Removes the tenant's chains; from now on its whole base path answers 404. */
  public ScopeReloadResult remove(final String physicalTenantId) {
    requireNotDefault(physicalTenantId);
    return locked(
        physicalTenantId,
        () -> {
          final var result =
              port.get().remove(PhysicalTenantScopeProvider.basePath(physicalTenantId));
          served.remove(physicalTenantId);
          scopes.remove(physicalTenantId);
          log(physicalTenantId, result);
          return result;
        });
  }

  /** Whether the tenant's chains serve on this node, so its requests may reach a controller. */
  public boolean serves(final String physicalTenantId) {
    return served.find(physicalTenantId).isPresent();
  }

  /** Whether the tenant's chains serve what the change asked for after it. */
  public static boolean takesEffect(final ScopeReloadResult result) {
    return result.applied() || result.outcome() == ScopeReloadOutcome.UNCHANGED;
  }

  private ScopeReloadResult record(
      final String physicalTenantId,
      final AuthenticationConfiguration authentication,
      final ScopeReloadResult result) {
    if (takesEffect(result)) {
      served.put(physicalTenantId, authentication);
    }
    log(physicalTenantId, result);
    return result;
  }

  private <T> T locked(final String physicalTenantId, final Supplier<T> change) {
    synchronized (locks.computeIfAbsent(physicalTenantId, id -> new Object())) {
      return change.get();
    }
  }

  private static void log(final String physicalTenantId, final ScopeReloadResult result) {
    if (takesEffect(result) || result.outcome() == ScopeReloadOutcome.ABSENT) {
      LOG.info(
          "HTTP security of physical tenant '{}': {} (generation {}, {} ms)",
          physicalTenantId,
          result.outcome(),
          result.generation(),
          result.duration().toMillis());
    } else {
      LOG.warn(
          "HTTP security of physical tenant '{}' was not changed: {} (keeps generation {}): {}",
          physicalTenantId,
          result.outcome(),
          result.generation(),
          result.failureCause().map(Throwable::getMessage).orElse("no cause"));
    }
  }

  private static void requireNotDefault(final String physicalTenantId) {
    if (DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      throw new IllegalArgumentException(
          "The HTTP security of the default physical tenant is the root configuration and changes"
              + " only with a node restart");
    }
  }
}
