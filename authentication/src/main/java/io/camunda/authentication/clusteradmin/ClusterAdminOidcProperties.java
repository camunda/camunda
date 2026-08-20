/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Binds and validates the cluster-admin OIDC authorization config from {@code
 * camunda.security.cluster-admin.oidc.*}: the client ids, groups, and generic claims that grant
 * full cluster-admin access.
 */
public final class ClusterAdminOidcProperties {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterAdminOidcProperties.class);

  private static final String PREFIX = "camunda.security.cluster-admin.oidc";
  private static final String CLIENTS_PROPERTY = PREFIX + ".clients";
  private static final String GROUPS_PROPERTY = PREFIX + ".groups";
  private static final String CLAIMS_PROPERTY = PREFIX + ".claims";

  private final List<String> clients;
  private final List<String> groups;
  private final List<ClusterAdminClaim> claims;

  private ClusterAdminOidcProperties(
      final List<String> clients, final List<String> groups, final List<ClusterAdminClaim> claims) {
    this.clients = clients;
    this.groups = groups;
    this.claims = claims;
  }

  /**
   * Binds the cluster-admin OIDC config and validates it against the provider's claim names.
   *
   * @param environment the Spring {@link Environment} to bind from
   * @param clientIdClaim the provider's configured {@code client-id-claim} (may be {@code null})
   * @param groupsClaim the provider's configured {@code groups-claim} (may be {@code null})
   * @return the validated config
   * @throws IllegalStateException on a blank client/group, a claim with a blank name/value, or a
   *     client/group matcher whose required provider claim name is not configured
   */
  public static ClusterAdminOidcProperties loadAndValidate(
      final Environment environment, final String clientIdClaim, final String groupsClaim) {
    final Binder binder = Binder.get(environment);
    final List<String> clients =
        binder.bind(CLIENTS_PROPERTY, Bindable.listOf(String.class)).orElse(List.of());
    final List<String> groups =
        binder.bind(GROUPS_PROPERTY, Bindable.listOf(String.class)).orElse(List.of());
    final List<ClusterAdminClaim> claims =
        binder.bind(CLAIMS_PROPERTY, Bindable.listOf(ClusterAdminClaim.class)).orElse(List.of());
    validate(clients, groups, claims, clientIdClaim, groupsClaim);
    // Empty config is legitimate (a deployment may run OIDC without cluster-admin yet), but the
    // chain is always active under OIDC and denies every token with no matchers — WARN so the
    // resulting lockout is visible rather than silently shipping an unreachable cluster-admin API.
    if (clients.isEmpty() && groups.isEmpty() && claims.isEmpty()) {
      LOG.warn(
          "No cluster-admin OIDC matchers configured ({}.*): every bearer token will be denied on "
              + "/cluster/v2/**. Configure at least one client, group, or claim to grant "
              + "cluster-admin access.",
          PREFIX);
    } else {
      LOG.info(
          "Loaded {} cluster-admin OIDC matcher(s) from {}.*",
          clients.size() + groups.size() + claims.size(),
          PREFIX);
    }
    return new ClusterAdminOidcProperties(clients, groups, claims);
  }

  public List<String> clients() {
    return clients;
  }

  public List<String> groups() {
    return groups;
  }

  public List<ClusterAdminClaim> claims() {
    return claims;
  }

  private static void validate(
      final List<String> clients,
      final List<String> groups,
      final List<ClusterAdminClaim> claims,
      final String clientIdClaim,
      final String groupsClaim) {
    rejectBlankEntry(clients, CLIENTS_PROPERTY, "client id");
    rejectBlankEntry(groups, GROUPS_PROPERTY, "group");
    validateClaims(claims);
    if (!clients.isEmpty() && isBlank(clientIdClaim)) {
      throw new IllegalStateException(
          "%s is configured but the OIDC provider has no 'client-id-claim'; client-id matching can never fire"
              .formatted(CLIENTS_PROPERTY));
    }
    if (!groups.isEmpty() && isBlank(groupsClaim)) {
      throw new IllegalStateException(
          "%s is configured but the OIDC provider has no 'groups-claim'; group matching can never fire"
              .formatted(GROUPS_PROPERTY));
    }
  }

  private static void rejectBlankEntry(
      final List<String> values, final String property, final String label) {
    for (final String value : values) {
      if (isBlank(value)) {
        throw new IllegalStateException("%s contains a blank %s".formatted(property, label));
      }
    }
  }

  private static void validateClaims(final List<ClusterAdminClaim> claims) {
    for (final ClusterAdminClaim claim : claims) {
      if (isBlank(claim.name())) {
        throw new IllegalStateException(
            "%s contains an entry with a blank or missing 'name'".formatted(CLAIMS_PROPERTY));
      }
      if (isBlank(claim.value())) {
        throw new IllegalStateException(
            "%s entry '%s' has a blank or missing 'value'"
                .formatted(CLAIMS_PROPERTY, claim.name()));
      }
    }
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
