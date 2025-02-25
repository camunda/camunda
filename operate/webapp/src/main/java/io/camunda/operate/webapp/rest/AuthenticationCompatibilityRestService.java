/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.webapp.rest.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!consolidated-auth")
public class AuthenticationCompatibilityRestService {
  @Autowired private AuthenticationRestService authenticationRestService;

  @GetMapping(path = "/v2/authentication/me")
  public UserDto getCurrentAuthentication() {
    return authenticationRestService.getCurrentAuthentication();
  }
}
