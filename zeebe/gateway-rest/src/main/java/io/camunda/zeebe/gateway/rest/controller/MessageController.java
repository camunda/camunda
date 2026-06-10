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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
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

  private final ServiceRegistry serviceRegistry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final PhysicalTenantRestConfigProvider tenantRestConfigProvider;

  public MessageController(
      final ServiceRegistry serviceRegistry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider,
      final PhysicalTenantRestConfigProvider tenantRestConfigProvider) {
    this.serviceRegistry = serviceRegistry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
    this.tenantRestConfigProvider = tenantRestConfigProvider;
  }

  @CamundaPostMapping(path = "/publication")
  public CompletableFuture<ResponseEntity<Object>> publishMessage(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final MessagePublicationRequest publicationRequest) {
    return RequestMapper.toMessagePublicationRequest(
            publicationRequest,
            multiTenancyCfg.isChecksEnabled(),
            tenantRestConfigProvider.forTenant(physicalTenantId).getMaxNameFieldLength())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> publishMessage(serviceRegistry.messageServices(physicalTenantId), mapped));
  }

  @CamundaPostMapping(path = "/correlation")
  public CompletableFuture<ResponseEntity<Object>> correlateMessage(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final MessageCorrelationRequest correlationRequest) {
    return RequestMapper.toMessageCorrelationRequest(
            correlationRequest,
            multiTenancyCfg.isChecksEnabled(),
            tenantRestConfigProvider.forTenant(physicalTenantId).getMaxNameFieldLength())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> correlateMessage(serviceRegistry.messageServices(physicalTenantId), mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> correlateMessage(
      final MessageServices messageServices, final CorrelateMessageRequest correlationRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> messageServices.correlateMessage(correlationRequest, authentication),
        ResponseMapper::toMessageCorrelationResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> publishMessage(
      final MessageServices messageServices, final PublicationMessageRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> messageServices.publishMessage(request, authentication),
        ResponseMapper::toMessagePublicationResponse,
        HttpStatus.OK);
  }
}
