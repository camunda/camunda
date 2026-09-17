/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.in.AuthorizationCheckPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Fails the OIDC callback for a user who may not access Optimize, so no session is created in the
 * first place. CSL installs this on the webapp login and turns the failure into a terminal 401, not
 * into another redirect to the IdP, which would loop.
 *
 * <p>The per-request counterpart is CSL's webapp authorization filter, which asks the same policy
 * through the {@link AuthorizationCheckPort} adapter.
 */
public final class OptimizeComponentAccessOidcUserService extends OidcUserService {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeComponentAccessOidcUserService.class);

  private final OptimizeComponentAccessPolicy policy;

  public OptimizeComponentAccessOidcUserService(final OptimizeComponentAccessPolicy policy) {
    this.policy = policy;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser user = super.loadUser(userRequest);
    policy
        .loginDenialReason(userRequest.getAccessToken().getTokenValue(), user.getClaims())
        .ifPresent(
            reason -> {
              LOG.info("Denying login for '{}': {}", user.getName(), reason);
              throw new OAuth2AuthenticationException(
                  new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, reason, null), reason);
            });
    return user;
  }
}
