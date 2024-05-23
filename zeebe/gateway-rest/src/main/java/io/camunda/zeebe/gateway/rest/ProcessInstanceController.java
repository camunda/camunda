/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.query.SearchQuery;
import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ZeebeRestController
public class ProcessInstanceController {

  private final ProcessInstanceServices processInstanceServices;

  @Autowired
  public ProcessInstanceController(final ProcessInstanceServices processInstanceServices) {
    this.processInstanceServices = processInstanceServices;
  }

  @PostMapping(
      path = "/process-instances/search1",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProcessInstanceSearchQueryResponse> searchProcessInstances(
      @RequestBody final ProcessInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(this::search, RestErrorMapper::mapProblemToResponse);
  }

  private ResponseEntity<ProcessInstanceSearchQueryResponse> search(
      final SearchQuery<ProcessInstanceFilter> query) {
    final var tenantIds = TenantAttributeHolder.tenantIds();
    final var result =
        processInstanceServices.withAuthentication((a) -> a.tenants(tenantIds)).search(query);
    return ResponseEntity.ok(
        SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result).get());
  }
}
