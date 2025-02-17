/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.service.AdHocSubprocessActivityServices;
import io.camunda.service.AdHocSubprocessActivityServices.AdHocSubprocessActivity;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
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

  @CamundaPostMapping(path = "/activatable")
  public ResponseEntity<AdHocSubprocessActivitySearchQueryResult> findActivatableActivities(
      @RequestBody final AdHocSubprocessActivitySearchQuery request) {
    try {
      final var activities =
          adHocSubprocessActivityServices
              .withAuthentication(RequestMapper.getAuthentication())
              .findActivatableActivities(
                  Long.valueOf(request.getProcessDefinitionKey()), request.getAdHocSubprocessId());

      final var result = new AdHocSubprocessActivitySearchQueryResult();
      result.setItems(activities.stream().map(this::toAdHocActivity).toList());

      return ResponseEntity.ok(result);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private AdHocSubprocessActivityResult toAdHocActivity(final AdHocSubprocessActivity activity) {
    final var result = new AdHocSubprocessActivityResult();
    result.setProcessDefinitionKey(activity.processDefinitionKey().toString());
    result.setAdHocSubprocessId(activity.adHocSubprocessId());
    result.setFlowNodeId(activity.flowNodeId());
    result.setFlowNodeName(activity.flowNodeName());
    result.setType(AdHocSubprocessActivityResult.TypeEnum.fromValue(activity.type().name()));
    result.setDocumentation(activity.documentation());
    return result;
  }
}
