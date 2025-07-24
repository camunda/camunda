/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final String groupsClaim;

  public CamundaOAuthPrincipalServiceNoDbImpl(final SecurityConfiguration securityConfiguration) {
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    oidcGroupsLoader = new OidcGroupsLoader(groupsClaim);
  }

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    LOG.debug("Loading OAuth context in no-db mode for claims: {}", claims);

    final var authContextBuilder = new AuthenticationContextBuilder();
    final var principals = oidcPrincipalLoader.load(claims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }

    if (username != null) {
      authContextBuilder.withUsername(username);
    }

    if (clientId != null) {
      authContextBuilder.withClientId(clientId);
    }

    // group check must remain enabled for no-db mode
    final Set<String> groups;
    final boolean groupsClaimPresent = StringUtils.hasText(groupsClaim);
    if (groupsClaimPresent) {
      groups = new HashSet<>(oidcGroupsLoader.load(claims));
    } else {
      groups = Collections.emptySet();
    }

    // In no-db mode, we don't access secondary storage services:
    // - No mapping lookups
    // - No role lookups from secondary storage
    // - No tenant lookups from secondary storage
    // - No authorized applications from secondary storage
    authContextBuilder
        .withAuthorizedApplications(Collections.emptyList())
        .withTenants(Collections.emptyList())
        .withGroups(groups.stream().toList())
        .withRoles(Collections.emptyList())
        .withGroupsClaimEnabled(groupsClaimPresent);

    LOG.debug(
        "Created OAuth context in no-db mode for user: {}, client: {}, groups: {}",
        username,
        clientId,
        groups);

    return new OAuthContext(Collections.emptySet(), authContextBuilder.build());
  }
}
