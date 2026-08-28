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
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.core.env.Environment;

/**
 * {@link CamundaSecurityScopeProvider} that emits one {@link ScopedSecurityDescriptor} per
 * explicitly configured physical tenant, plus an alias descriptor for the implicit {@code default}
 * tenant — or nothing at all when no physical tenant is configured.
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
 * <p><b>Default alias:</b> when at least one physical tenant is configured, a descriptor for the
 * implicit {@code default} tenant is emitted at {@code /physical-tenants/default}, built from
 * {@code forPhysicalTenant("default")}, so the root tenant is addressable both unprefixed ({@code
 * /v2/...}) and through the alias.
 *
 * <p>With none configured this provider emits nothing and the cluster chain serves the alias paths
 * from its own {@code /physical-tenants/default}-prefixed path sets. CSL builds two chains per
 * descriptor, each resolving its own {@code ClientRegistration} through a blocking discovery call,
 * so emitting no descriptor saves two such calls per provider at startup.
 */
public final class PhysicalTenantScopeProvider implements CamundaSecurityScopeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantScopeProvider.class);

  private final Environment environment;
  private final List<ScopedSecurityDescriptor> descriptors;

  public PhysicalTenantScopeProvider(final Environment environment) {
    this.environment = environment;
    descriptors = buildDescriptors();
  }

  @Override
  public List<ScopedSecurityDescriptor> get() {
    return descriptors;
  }

  /**
   * Whether any physical tenant is configured (any key under {@code
   * camunda.physical-tenants.<id>.*} with a valid id).
   *
   * <p>Two things depend on this and must agree: whether the cluster {@code /v2} chain uses the
   * default tenant's config (see {@code PhysicalTenantSecurityConfiguration}), and whether scoped
   * descriptors are emitted. Always call this instead of checking the config yourself — if the two
   * ever disagreed, a scoped chain would prefix a path that already has the prefix.
   */
  public static boolean hasConfiguredPhysicalTenants(final Environment environment) {
    return !discoverExplicitTenantIds(environment).isEmpty();
  }

  private List<ScopedSecurityDescriptor> buildDescriptors() {
    final Set<String> tenantIds = descriptorTenantIds();
    final List<ScopedSecurityDescriptor> result = new ArrayList<>();
    for (final String tenantId : tenantIds) {
      addDescriptor(result, tenantId);
    }
    return List.copyOf(result);
  }

  /**
   * Every explicitly configured tenant, plus the {@code default} alias — or an empty set when no
   * tenant is configured, which is a supported outcome: CSL registers no scoped chains and the
   * cluster chain serves the alias paths itself.
   *
   * <p>The alias add is idempotent when {@code default} is also configured explicitly, and always
   * buildable, because a cluster serving the unprefixed {@code /v2} surface necessarily configures
   * its root provider.
   */
  private Set<String> descriptorTenantIds() {
    final Set<String> tenantIds = discoverExplicitTenantIds(environment);
    if (tenantIds.isEmpty()) {
      return tenantIds;
    }
    tenantIds.add(DEFAULT_PHYSICAL_TENANT_ID);
    return tenantIds;
  }

  private void addDescriptor(final List<ScopedSecurityDescriptor> result, final String tenantId) {
    try {
      final var authConfig =
          PhysicalTenantAuthConfigurations.forPhysicalTenant(tenantId, environment);
      final String basePath = PHYSICAL_TENANTS_PATH_SEGMENT + tenantId;
      result.add(new ScopedSecurityDescriptor(basePath, authConfig));
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
