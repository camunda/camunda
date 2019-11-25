/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.sso;

import java.util.Map;
import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.security.AbstractUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.auth0.jwt.interfaces.Claim;

@Component
@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class SSOUserService extends AbstractUserService {

  @Autowired
  private SSOWebSecurityConfig configuration;

  @Override
  public UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    TokenAuthentication tokenAuth = (TokenAuthentication) context.getAuthentication();
    return buildUserDtoFrom(tokenAuth);
  }

  private UserDto buildUserDtoFrom(TokenAuthentication tokenAuth) {
    Map<String, Claim> claims = tokenAuth.getClaims();
    String name = "No name";
    if (claims.containsKey(configuration.getNameKey())) {
      name = claims.get(configuration.getNameKey()).asString();
    }
    return UserDto.fromName(name);
  }
}
