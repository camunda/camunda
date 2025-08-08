/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.security.ConditionalOnSaaSConfigured;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@Profile("consolidated-auth")
@ConditionalOnSaaSConfigured
@CamundaRestController
@ConditionalOnSecondaryStorageEnabled
@RequestMapping("/v2/authentication")
public class SaaSTokenController {
  private final CamundaUserService camundaUserService;

  public SaaSTokenController(final CamundaUserService camundaUserService) {
    this.camundaUserService = camundaUserService;
  }

  @Hidden
  @CamundaGetMapping(path = "/me/token")
  public ResponseEntity<String> getCurrentToken() {
    final var token = camundaUserService.getUserToken();

    return token == null
        ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        : ResponseEntity.ok(token);
  }
}
