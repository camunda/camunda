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
import io.camunda.gateway.protocol.model.VariableSearchQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.VariableServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/variables")
public class VariableController {

  private final ServiceRegistry registry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public VariableController(
      final ServiceRegistry registry, final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchVariables(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final VariableSearchQuery query,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toVariableQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(registry.variableServices(physicalTenantId), q, truncateValues));
  }

  private ResponseEntity<Object> search(
      final VariableServices variableServices,
      final VariableQuery query,
      final boolean truncateValues) {
    try {
      final var result =
          variableServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{variableKey}")
  public ResponseEntity<Object> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("variableKey") final Long variableKey) {
    try {
      // Success case: Return the left side with the VariableItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toVariableItem(
                  registry
                      .variableServices(physicalTenantId)
                      .getByKey(variableKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
