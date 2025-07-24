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
import io.camunda.authentication.service.SecondaryStorageMembershipService;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@ConditionalOnSecondaryStorageEnabled
public class CamundaOAuthPrincipalServiceImpl implements CamundaOAuthPrincipalService {

  private final String usernameClaim;
  private final String clientIdClaim;
  private final MembershipService membershipService;
  private final OidcPrincipalLoader oidcPrincipalLoader;

  public CamundaOAuthPrincipalServiceImpl(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices,
      final SecurityConfiguration securityConfiguration) {
    final String groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    final OidcGroupsLoader oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);

    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    membershipService =
        new SecondaryStorageMembershipService(
            mappingRuleServices,
            tenantServices,
            roleServices,
            groupServices,
            authorizationServices,
            groupsClaim,
            oidcGroupsLoader);
  }

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final var principalResult =
        extractPrincipals(oidcPrincipalLoader, claims, usernameClaim, clientIdClaim);
    return membershipService.resolveMemberships(claims, principalResult);
  }
}
