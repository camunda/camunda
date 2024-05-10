/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.authentication.basic;

import io.camunda.identity.record.CamundaOidcUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@Profile("identity-basic-auth")
public class CamundaOidcUserService extends OidcUserService {

  final CamundaUserDetailsManager userDetailsManager;

  public CamundaOidcUserService(final CamundaUserDetailsManager userDetailsManager) {
    this.userDetailsManager = userDetailsManager;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser oidcUser = super.loadUser(userRequest);
    final UserDetails details = userDetailsManager.loadUserByUsername(oidcUser.getName());
    if (details != null) {
      return new CamundaOidcUser(
          oidcUser, details.getAuthorities().stream().map(a -> (GrantedAuthority) a).toList());
    }
    return oidcUser;
  }
}
