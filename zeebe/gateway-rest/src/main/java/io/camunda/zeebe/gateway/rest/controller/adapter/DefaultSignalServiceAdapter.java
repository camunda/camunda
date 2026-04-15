/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.SignalBroadcastRequestContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.SignalServices;
import io.camunda.zeebe.gateway.rest.controller.generated.SignalServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultSignalServiceAdapter implements SignalServiceAdapter {

  private final SignalServices signalServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultSignalServiceAdapter(
      final SignalServices signalServices, final MultiTenancyConfiguration multiTenancyCfg) {
    this.signalServices = signalServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> broadcastSignal(
      final SignalBroadcastRequestContract requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toBroadcastSignalRequest(requestStrict, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        signalServices.broadcastSignal(
                            mapped.signalName(),
                            mapped.variables(),
                            mapped.tenantId(),
                            authentication),
                    ResponseMapper::toSignalBroadcastResponse,
                    HttpStatus.OK));
  }
}
