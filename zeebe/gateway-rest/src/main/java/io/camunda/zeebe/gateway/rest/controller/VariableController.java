/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.gateway.protocol.rest.GlobalVariableCreateQuery;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQuery;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/variables")
public class VariableController {

  private final VariableServices variableServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public VariableController(
      final VariableServices variableServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.variableServices = variableServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchVariables(
      @RequestBody(required = false) final VariableSearchQuery query) {
    return SearchQueryRequestMapper.toVariableQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaPutMapping(path = "/create")
  public CompletableFuture<ResponseEntity<Object>> createVariable(
      @RequestBody final GlobalVariableCreateQuery globalVariableCreateQuery) {
    return RequestMapper.executeServiceMethod(
        () -> {
          assert globalVariableCreateQuery.getKey() != null;
          assert globalVariableCreateQuery.getValue() != null;
          return variableServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .createVariable(
                  Map.of(globalVariableCreateQuery.getKey(), globalVariableCreateQuery.getValue()));
        },
        ResponseMapper::toGlobalVariableCreateResponse);
  }

  private ResponseEntity<Object> search(final VariableQuery query) {
    try {
      final var result =
          variableServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toVariableSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{variableKey}")
  public ResponseEntity<Object> getByKey(@PathVariable("variableKey") final Long variableKey) {
    try {
      // Success case: Return the left side with the VariableItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toVariableItem(
                  variableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getByKey(variableKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
