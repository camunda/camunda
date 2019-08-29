/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest;

import org.camunda.operate.webapp.sso.SSOWebSecurityConfig;
import static org.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;

import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.rest.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!"+SSOWebSecurityConfig.SSO_AUTH_PROFILE)
@RestController
@RequestMapping(value = AUTHENTICATION_URL)
public class AuthenticationRestService {

  public static final String AUTHENTICATION_URL = "/api/authentications/";
  public static final String USER_ENDPOINT = "/user";

  @Autowired
  private UserDetailsService userDetailsService;

  @GetMapping(path = USER_ENDPOINT)
  public UserDto getCurrentAuthentication() {
    SecurityContext context = SecurityContextHolder.getContext();
    Authentication authentication = context.getAuthentication();

    String username = authentication.getName();

    try {
      UserDetails userDetails = userDetailsService.loadUserByUsername(username);
      return UserDto.fromUserDetails(userDetails);
    }
    catch (UsernameNotFoundException e) {
      throw new UserNotFoundException(String.format("User '%s' not found.", username));
    }
  }

}