package org.camunda.operate.webapp.sso;
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */


import static org.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;

import java.util.Map;

import org.camunda.operate.webapp.rest.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.interfaces.Claim;

@Profile(SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@RestController
@RequestMapping(value = AUTHENTICATION_URL)
public class AuthenticationRestService {
  
  public static final String AUTHENTICATION_URL = "/api/authentications";
  
  @Autowired
  SSOWebSecurityConfig configuration;

  @GetMapping(path = "/user")
  public UserDto getCurrentAuthentication() {
    SecurityContext context = SecurityContextHolder.getContext();
    TokenAuthentication tokenAuth = (TokenAuthentication) context.getAuthentication();
    return buildUserDtoFrom(tokenAuth); 
  }

  private UserDto buildUserDtoFrom(TokenAuthentication tokenAuth) {
    Map<String, Claim> claims = tokenAuth.getClaims();
    String name = "No name";
    if(claims.containsKey(configuration.getNameKey())) {
      name = claims.get(configuration.getNameKey()).asString();
    }
    try {
      return UserDto.fromName(name);
    } catch (Throwable t) {
      return UserDto.fromName(tokenAuth.getName());
    }
  }

}
