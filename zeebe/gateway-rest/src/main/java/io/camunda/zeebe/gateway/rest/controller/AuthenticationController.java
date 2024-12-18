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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/authentication")
public class AuthenticationController {
  private final CamundaUserService camundaUserService;

  public AuthenticationController(final CamundaUserService camundaUserService) {
    this.camundaUserService = camundaUserService;
  }

  @GetMapping(
      path = "/me",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<CamundaUserDTO> getCurrentUser() {
    final var currentUser = camundaUserService.getCurrentUser();
    return currentUser != null
        ? new ResponseEntity<>(currentUser, HttpStatus.OK)
        : new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }
}
