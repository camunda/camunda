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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  /**
   * Transitional scaffolding. The target architecture gives each PT its own store; once per-PT
   * storage routing lands, each PT should run its own {@code IdentitySetupInitializer} against its
   * own store, fed only from that PT's init block. This flat-merge into the shared root
   * initialization can be deleted then.
   */
  @PostConstruct
  public void mergePerTenantInitialization() {
    final var tenants = properties.getPhysicalTenants();
    if (tenants == null || tenants.isEmpty()) {
      return;
    }
    final Set<String> knownIdps = collectKnownIdps(securityConfiguration);
    for (final PhysicalTenantConfiguration tenant : tenants) {
      validateTenant(tenant, knownIdps);
    }
    final InitializationConfiguration root = securityConfiguration.getInitialization();
    for (final PhysicalTenantConfiguration tenant : tenants) {
      mergeOne(root, tenant);
    }
  }

  private static Set<String> collectKnownIdps(final SecurityConfiguration sc) {
    final var providers = sc.getAuthentication().getProviders();
    if (providers != null && providers.getOidc() != null) {
      return providers.getOidc().keySet();
    }
    return Set.of();
  }

  private static void validateTenant(
      final PhysicalTenantConfiguration tenant, final Set<String> knownIdps) {
    if (tenant.getId() == null || tenant.getId().isBlank()) {
      throw new IllegalStateException("camunda.physical-tenants entry is missing a non-blank 'id'");
    }
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
    final InitializationConfiguration init = tenant.getSecurity().getInitialization();

    final int rolesAdded =
        appendDistinct(root.getRoles(), init.getRoles(), r -> r.roleId(), "role");
    final int groupsAdded =
        appendDistinct(root.getGroups(), init.getGroups(), g -> g.groupId(), "group");
    final int tenantsAdded =
        appendDistinct(root.getTenants(), init.getTenants(), t -> t.tenantId(), "logical tenant");
    root.getAuthorizations().addAll(init.getAuthorizations());

    LOG.info(
        "Merged physical tenant '{}': +{} roles, +{} groups, +{} tenants, +{} authorizations",
        tenant.getId(),
        rolesAdded,
        groupsAdded,
        tenantsAdded,
        init.getAuthorizations().size());
  }

  private static <T> int appendDistinct(
      final List<T> root,
      final List<T> incoming,
      final Function<T, String> idOf,
      final String label) {
    final Set<String> existingIds =
        root.stream().map(idOf).filter(Objects::nonNull).collect(Collectors.toSet());
    int added = 0;
    for (final T item : incoming) {
      final String id = idOf.apply(item);
      if (id != null && existingIds.contains(id)) {
        LOG.debug("Skipping duplicate {} '{}' from physical tenant init", label, id);
        continue;
      }
      root.add(item);
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
