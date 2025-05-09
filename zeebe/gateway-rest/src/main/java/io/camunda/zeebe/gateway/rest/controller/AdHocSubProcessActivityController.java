/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.AdHocSubProcessActivityQuery;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivitySearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances/ad-hoc-activities")
public class AdHocSubProcessActivityController {

  private final AdHocSubProcessActivityServices adHocSubProcessActivityServices;

  public AdHocSubProcessActivityController(
      final AdHocSubProcessActivityServices adHocSubProcessActivityServices) {
    this.adHocSubProcessActivityServices = adHocSubProcessActivityServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AdHocSubProcessActivitySearchQueryResult> searchAdHocSubProcessActivities(
      @RequestBody final AdHocSubProcessActivitySearchQuery query) {
    return RequestMapper.toAdHocSubProcessActivityQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::searchAdHocSubProcessActivities);
  }

  @CamundaPostMapping(path = "/{adHocSubProcessInstanceKey}/activation")
  public CompletableFuture<ResponseEntity<Object>> activateAdHocSubProcessActivities(
      @PathVariable final String adHocSubProcessInstanceKey,
      @RequestBody final AdHocSubProcessActivateActivitiesInstruction activationRequest) {
    return RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, activationRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::activateActivities);
  }

  private ResponseEntity<AdHocSubProcessActivitySearchQueryResult> searchAdHocSubProcessActivities(
      final AdHocSubProcessActivityQuery query) {
    try {
      final var activities =
          adHocSubProcessActivityServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);

      final var result = new AdHocSubProcessActivitySearchQueryResult();
      result.setItems(
          activities.items().stream()
              .map(SearchQueryResponseMapper::toAdHocSubProcessActivity)
              .toList());

      return ResponseEntity.ok(result);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> activateActivities(
      final AdHocSubProcessActivateActivitiesRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            adHocSubProcessActivityServices
                .withAuthentication(RequestMapper.getAuthentication())
                .activateActivities(request));
  }
}
