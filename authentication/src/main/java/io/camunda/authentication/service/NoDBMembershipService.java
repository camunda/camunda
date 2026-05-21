/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.api.model.auth.Memberships;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipPort {

  private final OidcGroupsExtractor oidcGroupsExtractor;
  private final boolean isGroupsClaimConfigured;

  public NoDBMembershipService(final SecurityConfiguration securityConfiguration) {
    oidcGroupsExtractor =
        new OidcGroupsExtractor(
            securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public Memberships resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final List<String> extracted =
        isGroupsClaimConfigured ? oidcGroupsExtractor.extract(tokenClaims) : List.of();
    // Dedup and produce an immutable list: matches the previous Set-backed semantics and
    // shields downstream from a mutable extractor result.
    final List<String> groups =
        extracted != null ? extracted.stream().distinct().toList() : List.of();
    return new Memberships(groups, List.of(), List.of(), List.of());
  }

  @Override
  public Memberships resolveMembershipsForUser(final String username) {
    // No secondary storage available — username/password flow has no DB-backed lookups.
    return Memberships.empty();
  }
}
