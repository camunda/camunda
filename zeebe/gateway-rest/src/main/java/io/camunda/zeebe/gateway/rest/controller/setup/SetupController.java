/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.setup;

import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/setup")
public class SetupController {
  private final UserServices userServices;

  public SetupController(final UserServices userServices) {
    this.userServices = userServices;
  }

  @CamundaPostMapping(path = "/user")
  public CompletableFuture<ResponseEntity<Object>> createAdminUser(
      @RequestBody final UserRequest request) {
    return RequestMapper.toUserDTO(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            dto ->
                RequestMapper.executeServiceMethod(
                    () ->
                        userServices
                            .withAuthentication(RequestMapper.getAnonymousAuthentication())
                            .createInitialAdminUser(dto),
                    ResponseMapper::toUserCreateResponse));
  }
}
