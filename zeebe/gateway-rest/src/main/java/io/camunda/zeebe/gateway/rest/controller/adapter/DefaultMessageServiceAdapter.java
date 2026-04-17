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
import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.MessageServices;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.controller.generated.MessageServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultMessageServiceAdapter implements MessageServiceAdapter {

  private final MessageServices messageServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final int maxNameFieldLength;

  public DefaultMessageServiceAdapter(
      final MessageServices messageServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.messageServices = messageServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.maxNameFieldLength = gatewayRestConfiguration.getMaxNameFieldLength();
  }

  @Override
  public ResponseEntity<Object> publishMessage(
      final MessagePublicationRequest request, final CamundaAuthentication authentication) {
    return RequestMapper.toMessagePublicationRequest(
            request, multiTenancyCfg.isChecksEnabled(), maxNameFieldLength)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> messageServices.publishMessage(mapped, authentication),
                    ResponseMapper::toMessagePublicationResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> correlateMessage(
      final MessageCorrelationRequest request, final CamundaAuthentication authentication) {
    return RequestMapper.toMessageCorrelationRequest(
            request, multiTenancyCfg.isChecksEnabled(), maxNameFieldLength)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> messageServices.correlateMessage(mapped, authentication),
                    ResponseMapper::toMessageCorrelationResponse,
                    HttpStatus.OK));
  }
}
