/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipService {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final String groupsClaim;

  public NoDBMembershipService(final SecurityConfiguration securityConfiguration) {
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final Map<String, Object> authenticatedClaims,
      final String username,
      final String clientId)
      throws OAuth2AuthenticationException {
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    final Set<String> groups =
        groupsClaimPresent
            ? new HashSet<>(oidcGroupsLoader.load(tokenClaims))
            : Collections.emptySet();

    return CamundaAuthentication.of(
        a ->
            a.user(username)
                .clientId(clientId)
                .roleIds(Collections.emptyList())
                .groupIds(groups.stream().toList())
                .mappingRule(Collections.emptyList())
                .tenants(Collections.emptyList())
                .claims(authenticatedClaims));
  }
}
