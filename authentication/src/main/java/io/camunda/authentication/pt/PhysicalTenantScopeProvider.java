/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.core.env.Environment;

/**
 * {@link CamundaSecurityScopeProvider} that emits one {@link ScopedSecurityDescriptor} per
 * explicitly configured physical tenant, plus an alias descriptor for the implicit {@code default}
 * tenant, which is always emitted.
 *
 * <p>Each descriptor carries:
 *
 * <ul>
 *   <li>A base path of {@code /physical-tenants/<id>}, matched by CSL against {@code basePath +
 *       apiPaths} to build a per-tenant API {@link
 *       org.springframework.security.web.SecurityFilterChain}.
 *   <li>A merged {@link io.camunda.security.api.model.config.AuthenticationConfiguration}
 *       containing the cluster providers (root ∪ PT overlay) merged with PT-side overrides, then
 *       narrowed to the tenant's {@code providers.assigned} selection (#54730) when it declares
 *       one.
 * </ul>
 *
 * <p><b>Default alias:</b> a descriptor for the implicit {@code default} tenant is always emitted
 * at {@code /physical-tenants/default}, built from {@code forPhysicalTenant("default")}, so the
 * root tenant is addressable both unprefixed ({@code /v2/...}) and through the alias, and the
 * descriptor list is never empty.
 *
 * <p><b>Live set:</b> the descriptors start as the tenants of the startup configuration and follow
 * the tenant lifecycle through {@link #put} and {@link #remove}, so {@link #get()} always returns
 * the current tenant set: what a tenant should be served with, whether or not its chains have taken
 * it yet. Each change publishes a new immutable descriptor list; a descriptor and its configuration
 * are never changed once handed out.
 */
public final class PhysicalTenantScopeProvider implements CamundaSecurityScopeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantScopeProvider.class);

  private final Environment environment;
  private final Object changeLock = new Object();
  private volatile Map<String, ScopedSecurityDescriptor> descriptors;

  public PhysicalTenantScopeProvider(final Environment environment) {
    this.environment = environment;
    descriptors = buildDescriptors();
  }

  @Override
  public List<ScopedSecurityDescriptor> get() {
    return List.copyOf(descriptors.values());
  }

  /** The descriptor a tenant should be served with, if it is part of the tenant set. */
  public Optional<ScopedSecurityDescriptor> descriptor(final String physicalTenantId) {
    return Optional.ofNullable(descriptors.get(physicalTenantId));
  }

  /**
   * Makes a tenant part of the set, or replaces what it should be served with.
   *
   * @param authentication a configuration built for this change alone; it must not be changed
   *     afterwards
   * @return the descriptor now emitted for the tenant
   */
  public ScopedSecurityDescriptor put(
      final String physicalTenantId, final AuthenticationConfiguration authentication) {
    final var descriptor = new ScopedSecurityDescriptor(basePath(physicalTenantId), authentication);
    synchronized (changeLock) {
      final var next = new LinkedHashMap<>(descriptors);
      next.put(physicalTenantId, descriptor);
      descriptors = Collections.unmodifiableMap(next);
    }
    return descriptor;
  }

  /** Takes a removed tenant out of the set. */
  public void remove(final String physicalTenantId) {
    synchronized (changeLock) {
      final var next = new LinkedHashMap<>(descriptors);
      next.remove(physicalTenantId);
      descriptors = Collections.unmodifiableMap(next);
    }
  }

  /** The base path of a tenant's scoped chains, {@code /physical-tenants/<id>}. */
  public static String basePath(final String physicalTenantId) {
    return PHYSICAL_TENANTS_PATH_SEGMENT + physicalTenantId;
  }

  /**
   * Whether any physical tenant is configured (any key under {@code
   * camunda.physical-tenants.<id>.*} with a valid id). When {@code true}, the cluster {@code /v2}
   * chain must be unified with the default tenant's resolved config (see {@code
   * PhysicalTenantSecurityConfiguration}), which is the only thing this gates.
   *
   * <p>It does <em>not</em> gate the {@code /physical-tenants/default} alias, which is always
   * emitted. With no {@code camunda.physical-tenants.*} keys the overlay bind is a no-op, so the
   * alias and the cluster chain resolve to equivalent config without the unification step.
   */
  public static boolean hasConfiguredPhysicalTenants(final Environment environment) {
    return !discoverExplicitTenantIds(environment).isEmpty();
  }

  private Map<String, ScopedSecurityDescriptor> buildDescriptors() {
    final Set<String> tenantIds = descriptorTenantIds();
    final Map<String, ScopedSecurityDescriptor> result = new LinkedHashMap<>();
    for (final String tenantId : tenantIds) {
      addDescriptor(result, tenantId);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * Every explicitly configured tenant, plus {@code default} — added unconditionally, so never
   * empty. The add is idempotent when {@code default} is also configured explicitly, and always
   * buildable, because a cluster serving the unprefixed {@code /v2} surface necessarily configures
   * its root provider.
   */
  private Set<String> descriptorTenantIds() {
    final Set<String> tenantIds = discoverExplicitTenantIds(environment);
    tenantIds.add(DEFAULT_PHYSICAL_TENANT_ID);
    return tenantIds;
  }

  private void addDescriptor(
      final Map<String, ScopedSecurityDescriptor> result, final String tenantId) {
    try {
      final var authConfig =
          PhysicalTenantAuthConfigurations.forPhysicalTenant(tenantId, environment);
      final String basePath = basePath(tenantId);
      result.put(tenantId, new ScopedSecurityDescriptor(basePath, authConfig));
      LOG.debug(
          "Registered scoped security descriptor for physical tenant '{}' at {} (providers: [{}])",
          tenantId,
          basePath,
          describeProviders(authConfig));
    } catch (final BindException | IllegalStateException e) {
      throw new IllegalStateException(
          "Failed to build scoped security configuration for physical tenant '%s': %s — cluster startup aborted."
              .formatted(tenantId, e.getMessage()),
          e);
    }
  }

  /**
   * Summarises a merged scope config for DEBUG diagnostics: each provider's registration id,
   * issuer, and audiences. Deliberately excludes client secrets and any credential material.
   */
  private static String describeProviders(final AuthenticationConfiguration auth) {
    final List<String> parts = new ArrayList<>();
    if (auth.getOidc() != null) {
      parts.add(
          "<default> issuer=%s aud=%s"
              .formatted(auth.getOidc().getIssuerUri(), auth.getOidc().getAudiences()));
    }
    if (auth.getProviders() != null && auth.getProviders().getOidc() != null) {
      auth.getProviders()
          .getOidc()
          .forEach(
              (id, p) ->
                  parts.add(
                      "%s issuer=%s aud=%s".formatted(id, p.getIssuerUri(), p.getAudiences())));
    }
    return String.join(", ", parts);
  }

  private static Set<String> discoverExplicitTenantIds(final Environment environment) {
    return PhysicalTenantAuthConfigurations.discoverExplicitTenantIds(environment);
  }
}
