/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.spring.utils.PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * {@link CamundaSecurityScopeProvider} that emits one {@link ScopedSecurityDescriptor} per
 * explicitly configured physical tenant, plus an alias descriptor for the implicit {@code default}
 * tenant whenever any tenant is configured.
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
 * <p><b>Default alias:</b> when at least one physical tenant is configured, this provider also
 * emits a descriptor for the implicit {@code default} tenant at {@code /physical-tenants/default},
 * built from {@code forPhysicalTenant("default")} (root + any {@code physical-tenants.default}
 * overlay, narrowed by its {@code assigned}). The default (root) tenant is therefore addressable
 * both via the unprefixed cluster paths ({@code /v2/...}) and via {@code
 * /physical-tenants/default/v2/...} as an alias — and {@code PhysicalTenantSecurityConfiguration}
 * feeds that same resolved config to CSL's cluster chain, so the two surfaces are identical. This
 * mirrors {@code PhysicalTenantResolver}'s synthesis of a default entry.
 *
 * <p><b>Empty-list behaviour:</b> if no {@code camunda.physical-tenants.*} entries are present in
 * the {@link Environment}, this provider returns an empty list (the default alias is <em>not</em>
 * emitted). The cluster then behaves identically to a non-PT deployment — CSL's standard chains
 * serve all traffic.
 */
public final class PhysicalTenantScopeProvider implements CamundaSecurityScopeProvider {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantScopeProvider.class);
  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";
  private static final ConfigurationPropertyName PREFIX_NAME =
      ConfigurationPropertyName.of(PHYSICAL_TENANTS_PREFIX);

  // Valid tenant id: lowercase alphanumeric, no dashes — so the yaml form
  // (camunda.physical-tenants.<id>.*) and its relaxed-binding env-var form address the same tenant.
  // Intentionally duplicated from PhysicalTenantResolver (configuration module): it is a one-line
  // rule, and no module that both 'configuration' and 'authentication' depend on fits a plain-Java
  // validator (spring-utils is Spring-specific; depending on 'configuration' would invert the
  // dependency). Keep the two in sync.
  private static final Pattern VALID_TENANT_ID = Pattern.compile("[a-z0-9]+");

  private final Environment environment;
  private final List<ScopedSecurityDescriptor> descriptors;

  public PhysicalTenantScopeProvider(final Environment environment) {
    this.environment = environment;
    this.descriptors = buildDescriptors();
  }

  @Override
  public List<ScopedSecurityDescriptor> get() {
    return descriptors;
  }

  /**
   * Whether any physical tenant is configured (any key under {@code
   * camunda.physical-tenants.<id>.*} with a valid id). When {@code true}, PT-scoped chains and the
   * {@code /physical-tenants/default} alias are active — and the cluster {@code /v2} chain must be
   * unified with the default tenant's resolved config (see {@code
   * PhysicalTenantSecurityConfiguration}).
   */
  public static boolean hasConfiguredPhysicalTenants(final Environment environment) {
    return !discoverExplicitTenantIds(environment).isEmpty();
  }

  private List<ScopedSecurityDescriptor> buildDescriptors() {
    final Set<String> tenantIds = discoverExplicitTenantIds(environment);
    if (tenantIds.isEmpty()) {
      LOG.debug("No camunda.physical-tenants.* entries found; PT-scoped security chains disabled.");
      return List.of();
    }

    // Expose the implicit default tenant as an alias for the root/cluster surface (the unprefixed
    // /v2/... paths), addressable at /physical-tenants/default — mirroring PhysicalTenantResolver's
    // synthesis of a default entry. A valid OIDC cluster always configures its root provider (the
    // unprefixed /v2 surface needs it), so the alias is always buildable; idempotent if default is
    // already configured explicitly.
    tenantIds.add(DEFAULT_PHYSICAL_TENANT_ID);

    final List<ScopedSecurityDescriptor> result = new ArrayList<>();
    for (final String tenantId : tenantIds) {
      addDescriptor(result, tenantId);
    }
    return List.copyOf(result);
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
    } catch (final IllegalStateException e) {
      // Log the exception itself (last arg) so the full cause chain — e.g. a deeply-nested Spring
      // Binder failure — is captured for operator diagnostics, not just its top-level message.
      LOG.warn(
          "Skipping scoped security chain for physical tenant '{}': {}",
          tenantId,
          e.getMessage(),
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

  /**
   * Walks the {@link Environment} and returns the set of explicitly configured physical-tenant ids
   * (those with at least one key under {@code camunda.physical-tenants.<id>.*}).
   *
   * <p>Tenant ids must be lowercase alphanumeric ({@code [a-z0-9]+}) — no dashes. This matches the
   * constraint enforced by {@code PhysicalTenantResolver} to keep yaml and env-var forms addressing
   * the same tenant.
   */
  private static Set<String> discoverExplicitTenantIds(final Environment environment) {
    final Set<String> tenants = new LinkedHashSet<>();
    for (final ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof final IterableConfigurationPropertySource iter) {
        iter.stream()
            .filter(PREFIX_NAME::isAncestorOf)
            .forEach(
                name -> {
                  if (name.getNumberOfElements() > PREFIX_NAME.getNumberOfElements()) {
                    final String tenantId =
                        name.getElement(
                            PREFIX_NAME.getNumberOfElements(),
                            ConfigurationPropertyName.Form.UNIFORM);
                    if (tenantId != null
                        && !tenantId.isEmpty()
                        && VALID_TENANT_ID.matcher(tenantId).matches()) {
                      tenants.add(tenantId);
                    }
                  }
                });
      }
    }
    return tenants;
  }
}
