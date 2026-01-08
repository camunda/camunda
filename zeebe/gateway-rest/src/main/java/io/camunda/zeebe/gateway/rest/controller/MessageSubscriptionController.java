/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQueryResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQueryResult;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class MessageSubscriptionController {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final MessageSubscriptionServices messageSubscriptionServices;

  public MessageSubscriptionController(
      final CamundaAuthenticationProvider authenticationProvider,
      final MessageSubscriptionServices messageSubscriptionServices) {
    this.authenticationProvider = authenticationProvider;
    this.messageSubscriptionServices = messageSubscriptionServices;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/message-subscriptions/search")
  public ResponseEntity<MessageSubscriptionSearchQueryResult> searchMessageSubscriptions(
      @RequestBody(required = false) final MessageSubscriptionSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toMessageSubscriptionQuery(searchRequest)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/correlated-message-subscriptions/search")
  public ResponseEntity<CorrelatedMessageSubscriptionSearchQueryResult>
      searchCorrelatedMessageSubscriptions(
          @RequestBody(required = false)
              final CorrelatedMessageSubscriptionSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toCorrelatedMessageSubscriptionQuery(searchRequest)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchCorrelatedMessageSubscriptions);
  }

  private ResponseEntity<CorrelatedMessageSubscriptionSearchQueryResult>
      searchCorrelatedMessageSubscriptions(final CorrelatedMessageSubscriptionQuery query) {
    try {
      final var result =
          messageSubscriptionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchCorrelated(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MessageSubscriptionSearchQueryResult> search(
      final MessageSubscriptionQuery query) {
    try {
      final var result =
          messageSubscriptionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
