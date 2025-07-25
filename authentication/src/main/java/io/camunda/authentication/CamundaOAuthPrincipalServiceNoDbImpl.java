/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.service.PrincipalExtractionHelper.extractPrincipals;

import io.camunda.authentication.entity.OAuthContext;
import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.search.util.ConditionalOnSecondaryStorageDisabled;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * No-operation implementation of CamundaOAuthPrincipalService for use when secondary storage is
 * disabled (camunda.database.type=none). This implementation only performs basic principal
 * extraction and groups claim processing, but does not access any secondary storage services for
 * mappings, roles, or tenants.
 */
@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@ConditionalOnSecondaryStorageDisabled
public class CamundaOAuthPrincipalServiceNoDbImpl implements CamundaOAuthPrincipalService {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaOAuthPrincipalServiceNoDbImpl.class);

  private final String usernameClaim;
  private final String clientIdClaim;
  private final MembershipService membershipService;
  private final OidcPrincipalLoader oidcPrincipalLoader;

  public CamundaOAuthPrincipalServiceNoDbImpl(final SecurityConfiguration securityConfiguration) {
    final String groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    final OidcGroupsLoader oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);

    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    membershipService = new NoDBMembershipService(oidcGroupsLoader, groupsClaim);
  }

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    LOG.debug("Loading OAuth context in no-db mode for claims: {}", claims);
    final var principalResult =
        extractPrincipals(oidcPrincipalLoader, claims, usernameClaim, clientIdClaim);
    return membershipService.resolveMemberships(claims, principalResult);
  }
}
