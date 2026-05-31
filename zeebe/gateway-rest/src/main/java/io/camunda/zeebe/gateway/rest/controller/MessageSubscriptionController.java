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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class MessageSubscriptionController {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ServiceRegistry serviceRegistry;

  public MessageSubscriptionController(
      final CamundaAuthenticationProvider authenticationProvider,
      final ServiceRegistry serviceRegistry) {
    this.authenticationProvider = authenticationProvider;
    this.serviceRegistry = serviceRegistry;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/message-subscriptions/search")
  public ResponseEntity<MessageSubscriptionSearchQueryResult> searchMessageSubscriptions(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final MessageSubscriptionSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toMessageSubscriptionQuery(searchRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> search(serviceRegistry.messageSubscriptionServices(physicalTenantId), query));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/correlated-message-subscriptions/search")
  public ResponseEntity<CorrelatedMessageSubscriptionSearchQueryResult>
      searchCorrelatedMessageSubscriptions(
          @PhysicalTenantId final String physicalTenantId,
          @RequestBody(required = false)
              final CorrelatedMessageSubscriptionSearchQuery searchRequest) {
    return SearchQueryRequestMapper.toCorrelatedMessageSubscriptionQuery(searchRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query ->
                searchCorrelatedMessageSubscriptions(
                    serviceRegistry.messageSubscriptionServices(physicalTenantId), query));
  }

  private ResponseEntity<CorrelatedMessageSubscriptionSearchQueryResult>
      searchCorrelatedMessageSubscriptions(
          final MessageSubscriptionServices messageSubscriptionServices,
          final CorrelatedMessageSubscriptionQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = messageSubscriptionServices.searchCorrelated(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<MessageSubscriptionSearchQueryResult> search(
      final MessageSubscriptionServices messageSubscriptionServices,
      final MessageSubscriptionQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = messageSubscriptionServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
