/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = AUTHENTICATION_URL)
public class AuthenticationRestService extends InternalAPIErrorController {

  public static final String AUTHENTICATION_URL = "/api/authentications";
  public static final String USER_ENDPOINT = "/user";

  public static final String TOKEN_ENDPOINT = "/token";

  @Autowired private UserService userService;

  @GetMapping(path = USER_ENDPOINT)
  public UserDto getCurrentAuthentication() {
    try {
      return userService.getCurrentUser();
    } catch (final UsernameNotFoundException e) {
      throw new UserNotFoundException("Current user couldn't be found", e);
    }
  }

  @GetMapping(path = TOKEN_ENDPOINT)
  public String getCurrentUserToken() {
    return userService.getUserToken();
  }
}
