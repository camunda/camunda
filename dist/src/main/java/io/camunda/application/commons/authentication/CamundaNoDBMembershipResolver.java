/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.auth.domain.auth.OidcGroupsLoader;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.PrincipalType;
import io.camunda.auth.domain.spi.MembershipResolver;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves memberships in no-DB mode. Only extracts groups from JWT claims; returns empty roles,
 * tenants, and mapping rules. This mirrors the logic of {@code NoDBMembershipService} but targets
 * the auth SDK's {@link MembershipResolver} SPI.
 */
public class CamundaNoDBMembershipResolver implements MembershipResolver {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;

  public CamundaNoDBMembershipResolver(final SecurityConfiguration securityConfiguration) {
    oidcGroupsLoader =
        new OidcGroupsLoader(securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> claims,
      final String principalId,
      final PrincipalType principalType) {
    final Set<String> groups =
        isGroupsClaimConfigured
            ? new HashSet<>(oidcGroupsLoader.load(claims))
            : Collections.emptySet();

    return CamundaAuthentication.of(
        a -> {
          if (principalType == PrincipalType.CLIENT) {
            a.clientId(principalId);
          } else {
            a.user(principalId);
          }
          return a.roleIds(Collections.emptyList())
              .groupIds(groups.stream().toList())
              .mappingRule(Collections.emptyList())
              .tenants(Collections.emptyList())
              .claims(claims);
        });
  }
}
