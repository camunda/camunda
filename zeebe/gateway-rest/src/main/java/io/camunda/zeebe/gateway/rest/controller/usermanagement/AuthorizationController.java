/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.AuthorizationQuery;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class AuthorizationController {
  private final AuthorizationServices authorizationServices;

  public AuthorizationController(final AuthorizationServices authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @CamundaPostMapping(path = "/authorizations")
  public CompletableFuture<ResponseEntity<Object>> create(
      @RequestBody final AuthorizationRequest authorizationCreateRequest) {
    return RequestMapper.toCreateAuthorizationRequest(authorizationCreateRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::create);
  }

  @CamundaDeleteMapping(path = "/authorizations/{authorizationKey}")
  public CompletableFuture<ResponseEntity<Object>> deleteAuthorization(
      @PathVariable final long authorizationKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteAuthorization(authorizationKey));
  }

  @CamundaPostMapping(path = "/authorizations/search")
  public ResponseEntity<AuthorizationSearchResponse> searchAuthorizations(
      @RequestBody(required = false) final AuthorizationSearchQueryRequest query) {
    return SearchQueryRequestMapper.toAuthorizationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<AuthorizationSearchResponse> search(final AuthorizationQuery query) {
    try {
      final var result =
          authorizationServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAuthorizationSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> create(
      final CreateAuthorizationRequest createAuthorizationRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            authorizationServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createAuthorization(createAuthorizationRequest),
        ResponseMapper::toAuthorizationCreateResponse);
  }
}
