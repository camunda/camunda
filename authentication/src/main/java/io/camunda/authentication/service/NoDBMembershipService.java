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

@Service
@ConditionalOnSecondaryStorageDisabled
public class NoDBMembershipService implements MembershipService {

  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;

  public NoDBMembershipService(final SecurityConfiguration securityConfiguration) {
    oidcGroupsLoader =
        new OidcGroupsLoader(securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType)
      throws OAuth2AuthenticationException {
    final Set<String> groups =
        isGroupsClaimConfigured
            ? new HashSet<>(oidcGroupsLoader.load(tokenClaims))
            : Collections.emptySet();

    return CamundaAuthentication.of(
        a -> {
          if (principalType.equals(PrincipalType.CLIENT)) {
            a.clientId(principalId);
          } else {
            a.user(principalId);
          }
          return a.roleIds(Collections.emptyList())
              .groupIds(groups.stream().toList())
              .mappingRule(Collections.emptyList())
              .tenants(Collections.emptyList())
              .claims(tokenClaims);
        });
  }
}
