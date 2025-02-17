/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.SignalServices;
import io.camunda.zeebe.gateway.protocol.rest.SignalBroadcastRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
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

  @Autowired
  public SignalController(
      final SignalServices signalServices, final MultiTenancyConfiguration multiTenancyCfg) {
    this.signalServices = signalServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping(path = "/broadcast")
  public CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      @RequestBody final SignalBroadcastRequest request) {
    return RequestMapper.toBroadcastSignalRequest(request, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::broadcastSignal);
  }

  private CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      final BroadcastSignalRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            signalServices
                .withAuthentication(RequestMapper.getAuthentication())
                .broadcastSignal(request.signalName(), request.variables(), request.tenantId()),
        ResponseMapper::toSignalBroadcastResponse);
  }
}
