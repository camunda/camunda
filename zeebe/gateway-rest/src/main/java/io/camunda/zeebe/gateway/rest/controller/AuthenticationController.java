/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.service.CamundaUserService;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;

@Profile("consolidated-auth")
@CamundaRestController
@RequestMapping("/v2/authentication")
public class AuthenticationController {
  private final CamundaUserService camundaUserService;

  public AuthenticationController(final CamundaUserService camundaUserService) {
    this.camundaUserService = camundaUserService;
  }

  @CamundaGetMapping(path = "/me")
  public CamundaUserDTO getCurrentUser() {
    return camundaUserService.getCurrentUser();
  }
}
