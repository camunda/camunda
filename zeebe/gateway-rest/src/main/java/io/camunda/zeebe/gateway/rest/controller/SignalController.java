/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.SignalBroadcastRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.SignalServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/signals")
public class SignalController {

  private final SignalServices signalServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  @Autowired
  public SignalController(
      final SignalServices signalServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.signalServices = signalServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/broadcast")
  public CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      @RequestBody final SignalBroadcastRequest request) {
    return RequestMapper.toBroadcastSignalRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::broadcastSignal);
  }

  private CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      final BroadcastSignalRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            signalServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .broadcastSignal(request.signalName(), request.variables(), request.tenantId()),
        ResponseMapper::toSignalBroadcastResponse);
  }
}
