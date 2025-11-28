/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ConditionalEventServices;
import io.camunda.zeebe.gateway.protocol.rest.ConditionalStartEventTriggerInstruction;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/conditions")
public class ConditionalEventController {

  private final ConditionalEventServices conditionalEventServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ConditionalEventController(
      final ConditionalEventServices conditionalEventServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.conditionalEventServices = conditionalEventServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> trigger(
      @RequestBody final ConditionalStartEventTriggerInstruction request) {
    return RequestMapper.toTriggerConditionalEvent(request, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::triggerConditionalEvent);
  }

  private CompletableFuture<ResponseEntity<Object>> triggerConditionalEvent(
      final ConditionalEventServices.ConditionalEventCreateRequest createRequest) {
    return conditionalEventServices
        .withAuthentication(authenticationProvider.getCamundaAuthentication())
        .triggerConditionalEvent(createRequest)
        .thenApply(ResponseEntity::ok);
  }
}
