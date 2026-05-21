/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.api.model.auth.MembershipProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Host-side {@link MembershipPort} for deployments without secondary storage. Only OIDC
 * groups-claim extraction is available — there is no DB to query for roles, tenants, or mapping
 * rules, so those always resolve to an empty list. The OIDC groups-claim parse is in-memory and
 * eager (fails fast on malformed input).
 */
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
  public MembershipProvider createProvider(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final List<String> extracted =
        isGroupsClaimConfigured ? oidcGroupsExtractor.extract(tokenClaims) : List.of();
    final List<String> groups =
        extracted != null ? extracted.stream().distinct().toList() : List.of();
    return new StaticProvider(groups);
  }

  @Override
  public MembershipProvider createProviderForUser(final String username) {
    // No secondary storage — BASIC flow has no DB-backed lookups and no claims-based groups.
    return new StaticProvider(List.of());
  }

  /** Returns the eagerly-resolved groups; everything else is empty. */
  private record StaticProvider(List<String> groups) implements MembershipProvider {
    @Override
    public List<String> roles() {
      return List.of();
    }

    @Override
    public List<String> tenants() {
      return List.of();
    }

    @Override
    public List<String> mappingRules() {
      return List.of();
    }
  }
}
