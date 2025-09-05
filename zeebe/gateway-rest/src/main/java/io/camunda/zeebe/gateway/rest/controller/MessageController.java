/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.search.query.CorrelatedMessageQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.zeebe.gateway.protocol.rest.CorrelatedMessageSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.CorrelatedMessageSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationRequest;
import io.camunda.zeebe.gateway.protocol.rest.MessagePublicationRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class MessageController {

  private final MessageServices messageServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public MessageController(
      final MessageServices messageServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.messageServices = messageServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/messages/publication")
  public CompletableFuture<ResponseEntity<Object>> publishMessage(
      @RequestBody final MessagePublicationRequest publicationRequest) {
    return RequestMapper.toMessagePublicationRequest(
            publicationRequest, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::publishMessage);
  }

  @CamundaPostMapping(path = "/messages/correlation")
  public CompletableFuture<ResponseEntity<Object>> correlateMessage(
      @RequestBody final MessageCorrelationRequest correlationRequest) {
    return RequestMapper.toMessageCorrelationRequest(
            correlationRequest, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::correlateMessage);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/correlated-messages/search")
  public ResponseEntity<CorrelatedMessageSearchQueryResult> searchCorrelatedMessages(
      @RequestBody(required = false) final CorrelatedMessageSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toCorrelatedMessageQuery(searchRequest)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchCorrelatedMessages);
  }

  private ResponseEntity<CorrelatedMessageSearchQueryResult> searchCorrelatedMessages(
      final CorrelatedMessageQuery query) {
    try {
      final var result =
          messageServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toCorrelatedMessageSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> correlateMessage(
      final CorrelateMessageRequest correlationRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            messageServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .correlateMessage(correlationRequest),
        ResponseMapper::toMessageCorrelationResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> publishMessage(
      final PublicationMessageRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            messageServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .publishMessage(request),
        ResponseMapper::toMessagePublicationResponse);
  }
}
