/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.entity.OAuthContext;
import io.camunda.security.auth.OidcGroupsLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.util.StringUtils;

public class NoDBMembershipService implements MembershipService {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final String groupsClaim;

  public NoDBMembershipService(final OidcGroupsLoader oidcGroupsLoader, final String groupsClaim) {
    this.oidcGroupsLoader = oidcGroupsLoader;
    this.groupsClaim = groupsClaim;
  }

  @Override
  public OAuthContext resolveMemberships(
      final java.util.Map<String, Object> claims,
      final PrincipalExtractionHelper.PrincipalExtractionResult principalResult)
      throws OAuth2AuthenticationException {
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    final Set<String> groups =
        groupsClaimPresent ? new HashSet<>(oidcGroupsLoader.load(claims)) : Collections.emptySet();
    return new OAuthContext(
        Collections.emptySet(),
        principalResult
            .authContextBuilder
            .withAuthorizedApplications(Collections.emptyList())
            .withTenants(Collections.emptyList())
            .withGroups(groups.stream().toList())
            .withRoles(Collections.emptyList())
            .withGroupsClaimEnabled(groupsClaimPresent)
            .build());
  }
}
