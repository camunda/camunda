/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/users")
public class UserController {
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;

  public UserController(final UserServices userServices, final PasswordEncoder passwordEncoder) {
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @RequestBody final CamundaUserWithPasswordRequest userWithPasswordDto) {

    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createUser(
                    userWithPasswordDto.getUsername(),
                    userWithPasswordDto.getName(),
                    userWithPasswordDto.getEmail(),
                    passwordEncoder.encode(userWithPasswordDto.getPassword())));
  }
}
