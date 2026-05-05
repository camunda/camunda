/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/messages")
public class MessageController {

  private final MessageServices messageServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final int maxNameFieldLength;

  public MessageController(
      final MessageServices messageServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.messageServices = messageServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
    this.maxNameFieldLength = gatewayRestConfiguration.getMaxNameFieldLength();
  }

  @CamundaPostMapping(path = "/publication")
  public CompletableFuture<ResponseEntity<Object>> publishMessage(
      @RequestBody final MessagePublicationRequest publicationRequest,
      @PhysicalTenant final String physicalTenantId) {
    return RequestMapper.toMessagePublicationRequest(
            publicationRequest, multiTenancyCfg.isChecksEnabled(), maxNameFieldLength)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> publishMessage(req, physicalTenantId));
  }

  @CamundaPostMapping(path = "/correlation")
  public CompletableFuture<ResponseEntity<Object>> correlateMessage(
      @RequestBody final MessageCorrelationRequest correlationRequest,
      @PhysicalTenant final String physicalTenantId) {
    return RequestMapper.toMessageCorrelationRequest(
            correlationRequest, multiTenancyCfg.isChecksEnabled(), maxNameFieldLength)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> correlateMessage(req, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> correlateMessage(
      final CorrelateMessageRequest correlationRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            messageServices.correlateMessage(correlationRequest, authentication, physicalTenantId),
        ResponseMapper::toMessageCorrelationResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> publishMessage(
      final PublicationMessageRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> messageServices.publishMessage(request, authentication, physicalTenantId),
        ResponseMapper::toMessagePublicationResponse,
        HttpStatus.OK);
  }
}
