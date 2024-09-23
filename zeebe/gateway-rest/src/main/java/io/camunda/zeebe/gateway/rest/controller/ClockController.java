/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ClockServices;
import io.camunda.zeebe.gateway.protocol.rest.ClockPinRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/clock")
public class ClockController {

  private final ClockServices clockServices;

  public ClockController(final ClockServices clockServices) {
    this.clockServices = clockServices;
  }

  @PutMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> pinClock(
      @RequestBody final ClockPinRequest pinRequest) {

    return RequestMapper.getPinnedEpoch(pinRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::pinClock);
  }

  @PostMapping(
      path = "/reset",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> resetClock() {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () -> clockServices.withAuthentication(RequestMapper.getAuthentication()).resetClock());
  }

  private CompletableFuture<ResponseEntity<Object>> pinClock(final long pinnedEpoch) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            clockServices
                .withAuthentication(RequestMapper.getAuthentication())
                .pinClock(pinnedEpoch));
  }
}
