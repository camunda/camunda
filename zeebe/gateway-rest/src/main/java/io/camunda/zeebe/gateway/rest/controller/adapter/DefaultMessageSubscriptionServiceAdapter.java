/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.zeebe.gateway.rest.controller.generated.MessageSubscriptionServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultMessageSubscriptionServiceAdapter implements MessageSubscriptionServiceAdapter {

  private final MessageSubscriptionServices messageSubscriptionServices;

  public DefaultMessageSubscriptionServiceAdapter(
      final MessageSubscriptionServices messageSubscriptionServices) {
    this.messageSubscriptionServices = messageSubscriptionServices;
  }

  @Override
  public ResponseEntity<Object> searchMessageSubscriptions(
      final MessageSubscriptionSearchQuery messageSubscriptionSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toMessageSubscriptionQueryStrict(
            messageSubscriptionSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = messageSubscriptionServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchCorrelatedMessageSubscriptions(
      final CorrelatedMessageSubscriptionSearchQuery correlatedMessageSubscriptionSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toCorrelatedMessageSubscriptionQueryStrict(
            correlatedMessageSubscriptionSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    messageSubscriptionServices.searchCorrelated(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
                        result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
