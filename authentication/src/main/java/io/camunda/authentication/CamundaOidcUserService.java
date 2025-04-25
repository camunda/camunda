/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOidcUserService extends OidcUserService {

  private final CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  public CamundaOidcUserService(final CamundaOAuthPrincipalService camundaOAuthPrincipalService) {
    this.camundaOAuthPrincipalService = camundaOAuthPrincipalService;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser oidcUser = super.loadUser(userRequest);
    final Map<String, Object> claims = userRequest.getIdToken().getClaims();
    return new CamundaOidcUser(oidcUser, camundaOAuthPrincipalService.loadOAuthContext(claims));
  }
}
