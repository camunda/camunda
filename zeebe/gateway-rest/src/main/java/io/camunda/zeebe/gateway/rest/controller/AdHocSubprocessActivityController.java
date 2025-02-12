/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.AdHocSubprocessActivityQuery;
import io.camunda.service.AdHocSubprocessActivityServices;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/ad-hoc-activities")
public class AdHocSubprocessActivityController {

  private final AdHocSubprocessActivityServices adHocSubprocessActivityServices;
  private final ProcessCache processCache;

  public AdHocSubprocessActivityController(
      final AdHocSubprocessActivityServices adHocSubprocessActivityServices,
      final ProcessCache processCache) {
    this.adHocSubprocessActivityServices = adHocSubprocessActivityServices;
    this.processCache = processCache;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AdHocSubprocessActivitySearchQueryResult> searchAdHocSubprocessActivities(
      @RequestBody final AdHocSubprocessActivitySearchQuery query) {
    return SearchQueryRequestMapper.toAdHocSubprocessActivityQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<AdHocSubprocessActivitySearchQueryResult> search(
      final AdHocSubprocessActivityQuery query) {
    try {
      final var result =
          adHocSubprocessActivityServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      final var processCacheItems = processCache.getFlowNodeNames(result.items());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAdHocSubprocessActivitySearchQueryResponse(
              result, processCacheItems));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
