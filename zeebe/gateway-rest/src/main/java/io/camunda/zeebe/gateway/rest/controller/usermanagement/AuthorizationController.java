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
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/authorizations")
public class AuthorizationController {
  private final AuthorizationServices authorizationServices;

  public AuthorizationController(final AuthorizationServices authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @PatchMapping(
      path = "/{ownerKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> patchAuthorization(
      @PathVariable final long ownerKey,
      @RequestBody final AuthorizationPatchRequest authorizationPatchRequest) {
    return RequestMapper.toAuthorizationPatchRequest(ownerKey, authorizationPatchRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::patchAuthorization);
  }

  @PostMapping(
      path = "/v2/authorizations/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthorizationSearchResponse> searchAuthorizations(
      @RequestBody(required = false) final AuthorizationSearchQueryRequest query) {
    return SearchQueryRequestMapper.toAuthorizationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @PostMapping(
      path = "/v2/users/{userKey}/authorizations/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthorizationSearchResponse> searchUserAuthorizations(
      @PathVariable("userKey") final long userKey,
      @RequestBody(required = false) final AuthorizationSearchQueryRequest query) {
    var finalQuery = query;
    if (query == null) {
      finalQuery = new AuthorizationSearchQueryRequest();
    }
    if (finalQuery.getFilter() == null) {
      finalQuery.setFilter(new AuthorizationFilterRequest());
    }
    finalQuery.getFilter().ownerType(OwnerTypeEnum.USER).ownerKey(userKey);
    return SearchQueryRequestMapper.toAuthorizationQuery(finalQuery)
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

  private CompletableFuture<ResponseEntity<Object>> patchAuthorization(
      final PatchAuthorizationRequest patchAuthorizationRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            authorizationServices
                .withAuthentication(RequestMapper.getAuthentication())
                .patchAuthorization(patchAuthorizationRequest));
  }
}
