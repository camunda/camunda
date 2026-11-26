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
import io.camunda.gateway.protocol.model.VariableHistoryItemSearchQuery;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
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
@RequestMapping("/v2/variables/history")
public class VariableHistoryController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public VariableHistoryController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchVariableHistory(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final VariableHistoryItemSearchQuery query,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toVariableHistoryItemQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(physicalTenantId, q, truncateValues));
  }

  @CamundaGetMapping(path = "/{variableHistoryItemKey}")
  public ResponseEntity<Object> getVariableHistoryItem(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("variableHistoryItemKey") final Long variableHistoryItemKey) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableHistoryItemResult(
              serviceRegistry
                  .variableHistoryServices(physicalTenantId)
                  .getByKey(
                      variableHistoryItemKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<Object> search(
      final String physicalTenantId,
      final VariableHistoryItemQuery query,
      final boolean truncateValues) {
    try {
      final var result =
          serviceRegistry
              .variableHistoryServices(physicalTenantId)
              .search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableHistoryItemSearchQueryResponse(
              result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
