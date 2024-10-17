/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationRequest;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/messages")
public class MessageController {

  private final MessageServices messageServices;
  private final MultiTenancyCfg multiTenancyCfg;

  public MessageController(
      final MessageServices messageServices, final MultiTenancyCfg multiTenancyCfg) {
    this.messageServices = messageServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @PostMapping(
      path = "/publication",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> publishMessage(
      @RequestBody final MessagePublicationRequest publicationRequest) {
    return RequestMapper.toMessagePublicationRequest(
            publicationRequest, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::publishMessage);
  }

  @PostMapping(
      path = "/correlation",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> correlateMessage(
      @RequestBody final MessageCorrelationRequest correlationRequest) {
    return RequestMapper.toMessageCorrelationRequest(
            correlationRequest, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::correlateMessage);
  }

  private CompletableFuture<ResponseEntity<Object>> correlateMessage(
      final CorrelateMessageRequest correlationRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            messageServices
                .withAuthentication(RequestMapper.getAuthentication())
                .correlateMessage(correlationRequest),
        ResponseMapper::toMessageCorrelationResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> publishMessage(
      final PublicationMessageRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            messageServices
                .withAuthentication(RequestMapper.getAuthentication())
                .publishMessage(request),
        ResponseMapper::toMessagePublicationResponse);
  }
}
