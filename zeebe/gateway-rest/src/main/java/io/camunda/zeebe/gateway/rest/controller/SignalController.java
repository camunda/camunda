/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.RequestMapper.BroadcastSignalRequest;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.SignalBroadcastRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.SignalServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/signals")
public class SignalController {

  private final ServiceRegistry registry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  @Autowired
  public SignalController(
      final ServiceRegistry registry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/broadcast")
  public CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final SignalBroadcastRequest request) {
    return RequestMapper.toBroadcastSignalRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> broadcastSignal(registry.signalServices(physicalTenantId), mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> broadcastSignal(
      final SignalServices signalServices, final BroadcastSignalRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            signalServices.broadcastSignal(
                request.signalName(), request.variables(), request.tenantId(), authentication),
        ResponseMapper::toSignalBroadcastResponse,
        HttpStatus.OK);
  }
}
