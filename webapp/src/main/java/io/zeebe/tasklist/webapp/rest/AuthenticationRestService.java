/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.rest;

import static io.zeebe.tasklist.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;

import io.zeebe.tasklist.webapp.rest.dto.UserDto;
import io.zeebe.tasklist.webapp.rest.exception.UserNotFoundException;
import io.zeebe.tasklist.webapp.security.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = AUTHENTICATION_URL)
public class AuthenticationRestService {

  public static final String AUTHENTICATION_URL = "/api/authentications";
  public static final String USER_ENDPOINT = "/user";

  @Autowired private UserService userService;

  @GetMapping(path = USER_ENDPOINT)
  public UserDto getCurrentAuthentication() {
    try {
      return userService.getCurrentUser();
    } catch (UsernameNotFoundException e) {
      throw new UserNotFoundException(
          String.format("User '%s' not found.", userService.getCurrentUsername()));
    }
  }
}
