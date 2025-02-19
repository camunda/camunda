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
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/ad-hoc-activities")
public class AdHocSubprocessActivityController {

  private final AdHocSubprocessActivityServices adHocSubprocessActivityServices;

  public AdHocSubprocessActivityController(
      final AdHocSubprocessActivityServices adHocSubprocessActivityServices) {
    this.adHocSubprocessActivityServices = adHocSubprocessActivityServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AdHocSubprocessActivitySearchQueryResult> searchAdHocSubprocessActivities(
      @RequestBody final AdHocSubprocessActivitySearchQuery query) {
    return RequestMapper.toAdHocSubprocessActivityQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchAdHocSubprocessActivities);
  }

  private ResponseEntity<AdHocSubprocessActivitySearchQueryResult> searchAdHocSubprocessActivities(
      final AdHocSubprocessActivityQuery query) {
    try {
      final var activities =
          adHocSubprocessActivityServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);

      final var result = new AdHocSubprocessActivitySearchQueryResult();
      result.setItems(
          activities.items().stream()
              .map(SearchQueryResponseMapper::toAdHocSubprocessActivity)
              .toList());

      return ResponseEntity.ok(result);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
