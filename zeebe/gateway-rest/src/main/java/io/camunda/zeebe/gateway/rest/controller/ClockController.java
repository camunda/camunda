/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.model.mapper.RequestMapper;
import io.camunda.gateway.protocol.model.ClockPinRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ClockServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/clock")
public class ClockController {

  private final ClockServices clockServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ClockController(
      final ClockServices clockServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.clockServices = clockServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> pinClock(
      @RequestBody final ClockPinRequest pinRequest) {

    return RequestMapper.getPinnedEpoch(pinRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::pinClock);
  }

  @CamundaPostMapping(
      path = "/reset",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> resetClock() {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            clockServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .resetClock());
  }

  private CompletableFuture<ResponseEntity<Object>> pinClock(final long pinnedEpoch) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            clockServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .pinClock(pinnedEpoch));
  }
}
