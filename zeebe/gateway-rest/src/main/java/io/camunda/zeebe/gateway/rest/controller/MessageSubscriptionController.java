/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/message-subscriptions")
public class MessageSubscriptionController {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final MessageSubscriptionServices messageSubscriptionServices;

  public MessageSubscriptionController(
      final CamundaAuthenticationProvider authenticationProvider,
      final MessageSubscriptionServices messageSubscriptionServices) {
    this.authenticationProvider = authenticationProvider;
    this.messageSubscriptionServices = messageSubscriptionServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<MessageSubscriptionSearchQueryResult> searchMessageSubscriptions(
      @RequestBody(required = false) final MessageSubscriptionSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toMessageSubscriptionQuery(searchRequest)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
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
