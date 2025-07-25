/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.search.util.ConditionalOnSecondaryStorageDisabled;
import io.camunda.security.auth.OidcGroupsLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService extends MembershipService {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final String groupsClaim;

  public NoDBMembershipService(final OidcGroupsLoader oidcGroupsLoader) {
    this.oidcGroupsLoader = oidcGroupsLoader;
    groupsClaim = oidcGroupsLoader.getGroupsClaim();
  }

  @Override
  public MembershipResult resolveMemberships(
      final java.util.Map<String, Object> claims, final String username, final String clientId)
      throws OAuth2AuthenticationException {
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    final Set<String> groups =
        groupsClaimPresent ? new HashSet<>(oidcGroupsLoader.load(claims)) : Collections.emptySet();
    return new MembershipResult(
        groups,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptySet(),
        Collections.emptyList());
  }
}
