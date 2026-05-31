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
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQueryResult;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/batch-operation-items")
public class BatchOperationItemsController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public BatchOperationItemsController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<BatchOperationItemSearchQueryResult> searchBatchOperationItems(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final BatchOperationItemSearchQuery query) {
    return SearchQueryRequestMapper.toBatchOperationItemQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.batchOperationServices(physicalTenantId), q));
  }

  private ResponseEntity<BatchOperationItemSearchQueryResult> search(
      final BatchOperationServices batchOperationServices, final BatchOperationItemQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = batchOperationServices.searchItems(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toBatchOperationItemSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
