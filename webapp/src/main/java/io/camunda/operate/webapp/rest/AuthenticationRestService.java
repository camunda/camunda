/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static io.camunda.operate.webapp.rest.AuthenticationRestService.AUTHENTICATION_URL;

@RestController
@RequestMapping(value = AUTHENTICATION_URL)
public class AuthenticationRestService {

  public static final String AUTHENTICATION_URL = "/api/authentications";
  public static final String USER_ENDPOINT = "/user";

  @Autowired
  private UserService userService;

  @GetMapping(path = USER_ENDPOINT)
  public UserDto getCurrentAuthentication() {
    try {
      return userService.getCurrentUser();
    }
    catch (UsernameNotFoundException e) {
      throw new UserNotFoundException("Current user couldn't be found", e);
    }
  }

}
