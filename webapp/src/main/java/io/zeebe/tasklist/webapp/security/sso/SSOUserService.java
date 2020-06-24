/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import com.auth0.jwt.interfaces.Claim;
import io.zeebe.tasklist.webapp.rest.dto.UserDto;
import io.zeebe.tasklist.webapp.security.AbstractUserService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class SSOUserService extends AbstractUserService {

  private static final String EMPTY = "";

  @Autowired private SSOWebSecurityConfig configuration;

  @Override
  public UserDto getCurrentUser() {
    final SecurityContext context = SecurityContextHolder.getContext();
    final TokenAuthentication tokenAuth = (TokenAuthentication) context.getAuthentication();
    return buildUserDtoFrom(tokenAuth);
  }

  private UserDto buildUserDtoFrom(TokenAuthentication tokenAuth) {
    final Map<String, Claim> claims = tokenAuth.getClaims();
    String name = "No name";
    if (claims.containsKey(configuration.getNameKey())) {
      name = claims.get(configuration.getNameKey()).asString();
    }
    return new UserDto().setFirstname(EMPTY).setLastname(name).setCanLogout(false);
  }
}
