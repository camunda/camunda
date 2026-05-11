/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.security.CamundaPhysicalTenantsConfiguration.CamundaPhysicalTenantsProperties;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.security.configuration.PhysicalTenantConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@code camunda.physical-tenants[]} and merges each tenant's {@code security.initialization}
 * lists into the top-level {@link SecurityConfiguration#getInitialization()} before the engine's
 * {@code IdentitySetupInitializer} reads it.
 *
 * <p>The merge is additive — top-level entries take precedence on id collision, per-tenant entries
 * are appended otherwise. Each appended entity carries a tenant-prefixed log line so operators can
 * trace which tenant block produced which row.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CamundaPhysicalTenantsProperties.class)
public class CamundaPhysicalTenantsConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaPhysicalTenantsConfiguration.class);

  private final CamundaPhysicalTenantsProperties properties;
  private final SecurityConfiguration securityConfiguration;

  public CamundaPhysicalTenantsConfiguration(
      final CamundaPhysicalTenantsProperties properties,
      final SecurityConfiguration securityConfiguration) {
    this.properties = properties;
    this.securityConfiguration = securityConfiguration;
  }

  /**
   * Exposes the bound {@code camunda.physical-tenants[]} list as a Spring bean so modules below
   * {@code dist} (notably {@code authentication}) can inject it without a reverse module
   * dependency.
   */
  @Bean
  public List<PhysicalTenantConfiguration> physicalTenantConfigurations() {
    final var tenants = properties.getPhysicalTenants();
    return tenants == null ? List.of() : tenants;
  }

  @PostConstruct
  public void mergePerTenantInitialization() {
    final var tenants = properties.getPhysicalTenants();
    if (tenants == null || tenants.isEmpty()) {
      return;
    }
    final Set<String> knownIdps = collectKnownIdps(securityConfiguration);
    final InitializationConfiguration root = securityConfiguration.getInitialization();
    for (final PhysicalTenantConfiguration tenant : tenants) {
      validateIdps(tenant, knownIdps);
      mergeOne(root, tenant);
    }
  }

  private static Set<String> collectKnownIdps(final SecurityConfiguration sc) {
    final var providers = sc.getAuthentication().getProviders();
    if (providers != null && providers.getOidc() != null) {
      return providers.getOidc().keySet();
    }
    return Collections.emptySet();
  }

  private static void validateIdps(
      final PhysicalTenantConfiguration tenant, final Set<String> knownIdps) {
    final var idps = tenant.getIdps();
    if (idps == null || idps.isEmpty()) {
      LOG.warn(
          "Physical tenant '{}' has no 'idps' configured — IdP filtering is disabled (passthrough)",
          tenant.getId());
      return;
    }
    for (final String idp : idps) {
      if (idp == null || idp.isBlank()) {
        throw new IllegalStateException(
            "camunda.physical-tenants[id=" + tenant.getId() + "].idps contains a blank entry");
      }
      if (!knownIdps.isEmpty() && !knownIdps.contains(idp)) {
        throw new IllegalStateException(
            "camunda.physical-tenants[id="
                + tenant.getId()
                + "].idps references unknown OIDC registration id '"
                + idp
                + "'. Known registration ids: "
                + knownIdps);
      }
    }
  }

  private static void mergeOne(
      final InitializationConfiguration root, final PhysicalTenantConfiguration tenant) {
    if (tenant.getId() == null || tenant.getId().isBlank()) {
      throw new IllegalStateException("camunda.physical-tenants entry is missing a non-blank 'id'");
    }
    final InitializationConfiguration init = tenant.getSecurity().getInitialization();
    final String tenantId = tenant.getId();

    final int rolesAdded = appendDistinctRoles(root, init);
    final int groupsAdded = appendDistinctGroups(root, init);
    final int tenantsAdded = appendDistinctTenants(root, init);
    final int authzAdded = root.getAuthorizations().size();
    root.getAuthorizations().addAll(init.getAuthorizations());
    final int authzAddedDelta = root.getAuthorizations().size() - authzAdded;

    LOG.info(
        "Merged physical tenant '{}': +{} roles, +{} groups, +{} tenants, +{} authorizations",
        tenantId,
        rolesAdded,
        groupsAdded,
        tenantsAdded,
        authzAddedDelta);
  }

  private static int appendDistinctRoles(
      final InitializationConfiguration root, final InitializationConfiguration tenantInit) {
    if (root.getRoles() == null) {
      root.setRoles(new ArrayList<>());
    }
    final var existingIds =
        root.getRoles().stream().map(r -> r.roleId()).filter(java.util.Objects::nonNull).toList();
    int added = 0;
    for (final var role : tenantInit.getRoles()) {
      if (role.roleId() != null && existingIds.contains(role.roleId())) {
        LOG.debug("Skipping duplicate role '{}' from physical tenant init", role.roleId());
        continue;
      }
      root.getRoles().add(role);
      added++;
    }
    return added;
  }

  private static int appendDistinctGroups(
      final InitializationConfiguration root, final InitializationConfiguration tenantInit) {
    if (root.getGroups() == null) {
      root.setGroups(new ArrayList<>());
    }
    final var existingIds =
        root.getGroups().stream().map(g -> g.groupId()).filter(java.util.Objects::nonNull).toList();
    int added = 0;
    for (final var group : tenantInit.getGroups()) {
      if (group.groupId() != null && existingIds.contains(group.groupId())) {
        LOG.debug("Skipping duplicate group '{}' from physical tenant init", group.groupId());
        continue;
      }
      root.getGroups().add(group);
      added++;
    }
    return added;
  }

  private static int appendDistinctTenants(
      final InitializationConfiguration root, final InitializationConfiguration tenantInit) {
    if (root.getTenants() == null) {
      root.setTenants(new ArrayList<>());
    }
    final var existingIds =
        root.getTenants().stream()
            .map(t -> t.tenantId())
            .filter(java.util.Objects::nonNull)
            .toList();
    int added = 0;
    for (final var t : tenantInit.getTenants()) {
      if (t.tenantId() != null && existingIds.contains(t.tenantId())) {
        LOG.debug("Skipping duplicate logical tenant '{}' from physical tenant init", t.tenantId());
        continue;
      }
      root.getTenants().add(t);
      added++;
    }
    return added;
  }

  @ConfigurationProperties(prefix = "camunda")
  public static final class CamundaPhysicalTenantsProperties {

    private List<PhysicalTenantConfiguration> physicalTenants = new ArrayList<>();

    public List<PhysicalTenantConfiguration> getPhysicalTenants() {
      return physicalTenants;
    }

    public void setPhysicalTenants(final List<PhysicalTenantConfiguration> physicalTenants) {
      this.physicalTenants = physicalTenants;
    }
  }
}
