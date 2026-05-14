/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipService {

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
  public MembershipResolver newResolver(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType)
      throws OAuth2AuthenticationException {
    // No secondary storage means roles/tenants/mappingRules can't be resolved at all, and groups
    // only come from the OIDC token claim when one is configured. Evaluate the claim eagerly so
    // malformed input fails fast.
    final List<String> groups =
        isGroupsClaimConfigured ? List.copyOf(oidcGroupsExtractor.extract(tokenClaims)) : List.of();
    return new StaticResolver(groups);
  }

  private record StaticResolver(List<String> groups) implements MembershipResolver {

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
