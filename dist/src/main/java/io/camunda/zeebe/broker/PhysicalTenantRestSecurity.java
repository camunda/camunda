/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.authentication.pt.PhysicalTenantAuthConfigurations;
import io.camunda.authentication.pt.PhysicalTenantHttpSecurity;
import io.camunda.security.api.model.scope.ScopeReloadOutcome;
import io.camunda.security.api.model.scope.ScopeReloadResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

/**
 * The REST side of a physical tenant change on this node: builds the tenant's authentication
 * configuration from the environment the change resolved against, exactly as the startup scope
 * provider does, and hands it to the security library through {@link PhysicalTenantHttpSecurity}.
 * Without scoped HTTP security on this node (no security library profile), it does nothing.
 *
 * <p>It remembers the last result per tenant, for the tenant's health and its rollout status.
 */
@NullMarked
public final class PhysicalTenantRestSecurity {

  private final @Nullable PhysicalTenantHttpSecurity httpSecurity;
  private final Map<String, Status> statuses = new ConcurrentHashMap<>();

  public PhysicalTenantRestSecurity(final @Nullable PhysicalTenantHttpSecurity httpSecurity) {
    this.httpSecurity = httpSecurity;
  }

  /** For a node without scoped HTTP security. */
  public static PhysicalTenantRestSecurity none() {
    return new PhysicalTenantRestSecurity(null);
  }

  /**
   * Adds or replaces the tenant's HTTP security with the configuration resolved from {@code
   * environment}.
   *
   * @return the library's result, or empty if this node has no scoped HTTP security
   */
  public Optional<ScopeReloadResult> apply(
      final String physicalTenantId, final Environment environment) {
    if (httpSecurity == null) {
      return Optional.empty();
    }
    final var authentication =
        PhysicalTenantAuthConfigurations.forPhysicalTenant(physicalTenantId, environment);
    return Optional.of(
        record(physicalTenantId, httpSecurity.apply(physicalTenantId, authentication)));
  }

  /** Forgets what a change that was not made asked for. */
  public void revert(final String physicalTenantId) {
    if (httpSecurity != null) {
      httpSecurity.revert(physicalTenantId);
    }
  }

  /** Removes the tenant's HTTP security, so that its whole base path answers 404. */
  public void remove(final String physicalTenantId) {
    if (httpSecurity != null) {
      httpSecurity.remove(physicalTenantId);
    }
    statuses.remove(physicalTenantId);
  }

  /** The result of the last change of the tenant's HTTP security on this node. */
  public Optional<Status> status(final String physicalTenantId) {
    return Optional.ofNullable(statuses.get(physicalTenantId));
  }

  private ScopeReloadResult record(final String physicalTenantId, final ScopeReloadResult result) {
    statuses.put(physicalTenantId, Status.of(result));
    return result;
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
}
