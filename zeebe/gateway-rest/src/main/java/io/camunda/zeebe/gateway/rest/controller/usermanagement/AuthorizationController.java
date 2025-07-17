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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/authorizations")
public class AuthorizationController {
  private final AuthorizationServices authorizationServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AuthorizationController(
      final AuthorizationServices authorizationServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.authorizationServices = authorizationServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping()
  public CompletableFuture<ResponseEntity<Object>> createAuthorization(
      @RequestBody final AuthorizationRequest authorizationCreateRequest) {
    return RequestMapper.toCreateAuthorizationRequest(authorizationCreateRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::create);
  }

  @CamundaGetMapping(path = "/{authorizationKey}")
  public ResponseEntity<Object> getAuthorization(@PathVariable final long authorizationKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toAuthorization(
                  authorizationServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getAuthorization(authorizationKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaDeleteMapping(path = "/{authorizationKey}")
  public CompletableFuture<ResponseEntity<Object>> delete(
      @PathVariable final long authorizationKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteAuthorization(authorizationKey));
  }

  @CamundaPutMapping(path = "/{authorizationKey}")
  public CompletableFuture<ResponseEntity<Object>> updateAuthorization(
      @PathVariable final long authorizationKey,
      @RequestBody final AuthorizationRequest authorizationUpdateRequest) {
    return RequestMapper.toUpdateAuthorizationRequest(authorizationKey, authorizationUpdateRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::update);
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AuthorizationSearchResult> searchAuthorizations(
      @RequestBody(required = false) final AuthorizationSearchQuery query) {
    return SearchQueryRequestMapper.toAuthorizationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<AuthorizationSearchResult> search(final AuthorizationQuery query) {
    try {
      final var result =
          authorizationServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
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
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createAuthorization(createAuthorizationRequest),
        ResponseMapper::toAuthorizationCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> update(
      final UpdateAuthorizationRequest authorizationRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateAuthorization(authorizationRequest));
  }
}
